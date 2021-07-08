package org.dashj.platform.dashpay

import java.util.Timer
import kotlin.concurrent.timerTask
import org.bouncycastle.crypto.params.KeyParameter
import org.dashevo.platform.Documents
import org.dashevo.platform.Platform
import org.dashj.platform.dapiclient.model.DocumentQuery
import org.dashj.platform.dashpay.callback.SendContactRequestCallback
import org.dashj.platform.dpp.document.Document
import org.dashj.platform.dpp.identifier.Identifier
import org.dashj.platform.dpp.identity.Identity

class ContactRequests(val platform: Platform) {

    companion object {
        const val CONTACTREQUEST_DOCUMENT = "dashpay.contactRequest"
    }

    fun create(fromUser: BlockchainIdentity, toUser: Identity, aesKey: KeyParameter?): ContactRequest {
        val contactKeyChain = fromUser.getReceiveFromContactChain(toUser, aesKey)
        val contactKey = contactKeyChain.watchingKey
        val contactPub = contactKey.serializeContactPub()

        val (encryptedContactPubKey, encryptedAccountLabel) = fromUser.encryptExtendedPublicKey(
            contactPub,
            toUser,
            0,
            aesKey
        )
        val accountReference = fromUser.getAccountReference(aesKey, toUser)

        val contactRequestDocument = ContactRequest.builder(platform)
            .to(toUser.id)
            .from(fromUser.uniqueIdentifier)
            .encryptedPubKey(encryptedContactPubKey, toUser.publicKeys[0].id, fromUser.identity!!.publicKeys[0].id)
            .accountReference(accountReference)
            .encryptedAccountLabel(encryptedAccountLabel)
            .build().document

        val transitionMap = hashMapOf(
            "create" to listOf(contactRequestDocument)
        )

        val transition = platform.dpp.document.createStateTransition(transitionMap)
        fromUser.signStateTransition(transition, aesKey)

        platform.broadcastStateTransition(transition)

        return ContactRequest(contactRequestDocument)
    }

    /**
     * Gets the contactRequest documents for the given userId
     * @param userId String
     * @param toUserId Boolean (true if getting toUserId, false if $userId)
     * @param afterTime Long Time in milliseconds
     * @param retrieveAll Boolean get all results (true) or 100 at a time (false)
     * @param startAt Int where to start getting results (1 is the first item)
     * @return List<Documents>
     */
    fun get(
        userId: String,
        toUserId: Boolean,
        afterTime: Long = 0,
        retrieveAll: Boolean = true,
        startAt: Int = 1
    ): List<Document> {
        return get(Identifier.from(userId), toUserId, afterTime, retrieveAll, startAt)
    }

    /**
     * Gets the contactRequest documents for the given userId
     * @param userId Identifier
     * @param toUserId Boolean (true if getting toUserId, false if $userId)
     * @param afterTime Long Time in milliseconds
     * @param retrieveAll Boolean get all results (true) or 100 at a time (false)
     * @param startAt Int where to start getting results (1 is the first item)
     * @return List<Documents>
     */

    fun get(
        userId: Identifier,
        toUserId: Boolean,
        afterTime: Long = 0,
        retrieveAll: Boolean = true,
        startAt: Int = 1
    ): List<Document> {
        val documentQuery = DocumentQuery.Builder()

        if (toUserId) {
            documentQuery.where(listOf("toUserId", "==", userId))
        } else {
            documentQuery.where(listOf("\$ownerId", "==", userId))
        }
        if (afterTime > 0) {
            documentQuery.where(listOf("\$createdAt", ">", afterTime))
        }
        if (retrieveAll) {
            documentQuery.limit(-1)
        } else {
            documentQuery.limit(Documents.DOCUMENT_LIMIT)
        }

        return platform.documents.getAll(
            CONTACTREQUEST_DOCUMENT,
            documentQuery.startAt(startAt).build()
        )
    }

    suspend fun watchContactRequest(
        fromUserId: Identifier,
        toUserId: Identifier,
        retryCount: Int,
        delayMillis: Long,
        retryDelayType: RetryDelayType
    ): Document? {
        val documentQuery = DocumentQuery.Builder()
        documentQuery.where("\$ownerId", "==", fromUserId)
            .where("toUserId", "==", toUserId)
        val result = platform.documents.get(CONTACTREQUEST_DOCUMENT, documentQuery.build())
        if (result.isNotEmpty()) {
            return result[0]
        } else {
            if (retryCount > 0) {
                val nextDelay = delayMillis * when (retryDelayType) {
                    RetryDelayType.SLOW20 -> 5 / 4
                    RetryDelayType.SLOW50 -> 3 / 2
                    else -> 1
                }
                kotlinx.coroutines.delay(nextDelay)
                return watchContactRequest(fromUserId, toUserId, retryCount - 1, nextDelay, retryDelayType)
            }
        }
        return null
    }

    fun watchContactRequest(
        fromUserId: Identifier,
        toUserId: Identifier,
        retryCount: Int,
        delayMillis: Long,
        retryDelayType: RetryDelayType,
        callback: SendContactRequestCallback
    ) {
        val documentQuery = DocumentQuery.builder()
        documentQuery.where("\$ownerId", "==", fromUserId)
            .where("toUserId", "==", toUserId)
        val result = platform.documents.get(CONTACTREQUEST_DOCUMENT, documentQuery.build())

        if (result.isNotEmpty()) {
            callback.onComplete(fromUserId, toUserId)
        } else {
            if (retryCount > 0) {
                Timer("monitorSendContactRequestStatus", false).schedule(
                    timerTask {
                        val nextDelay = delayMillis * when (retryDelayType) {
                            RetryDelayType.SLOW20 -> 5 / 4
                            RetryDelayType.SLOW50 -> 3 / 2
                            else -> 1
                        }
                        watchContactRequest(fromUserId, toUserId, retryCount - 1, nextDelay, retryDelayType, callback)
                    },
                    delayMillis
                )
            } else callback.onTimeout(fromUserId, toUserId)
        }
    }
}