/*
 * Copyright 2012 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Copyright (c) 2020-present, Dash Core Group
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */


package org.dashj.platform.tools;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.ByteString;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.DateConverter;
import org.bitcoinj.coinjoin.CoinJoinClientManager;
import org.bitcoinj.coinjoin.CoinJoinClientOptions;
import org.bitcoinj.coinjoin.CoinJoinSendRequest;
import org.bitcoinj.coinjoin.UnmixedZeroConfCoinSelector;
import org.bitcoinj.coinjoin.utils.CoinJoinReporter;
import org.bitcoinj.coinjoin.utils.ProTxToOutpoint;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.CheckpointManager;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.FullPrunedBlockChain;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.RejectMessage;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.BlocksDownloadedEventListener;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.core.listeners.PreBlocksDownloadListener;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.evolution.CreditFundingTransaction;
import org.bitcoinj.evolution.SimplifiedMasternodeList;
import org.bitcoinj.evolution.SimplifiedMasternodeListEntry;
import org.bitcoinj.net.discovery.ThreeMethodPeerDiscovery;
import org.bitcoinj.params.BinTangDevNetParams;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.protocols.payments.PaymentProtocol;
import org.bitcoinj.protocols.payments.PaymentProtocolException;
import org.bitcoinj.protocols.payments.PaymentSession;
import org.bitcoinj.script.Script.ScriptType;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.store.H2FullPrunedBlockStore;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.BtcAutoFormat;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.DerivationPathFactory;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.MarriedKeyChain;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletEx;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.bitcoinj.wallet.WalletExtension;
import org.bitcoinj.wallet.WalletProtobufSerializer;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.bitcoinj.wallet.listeners.WalletReorganizeEventListener;
import org.bouncycastle.crypto.params.KeyParameter;
import org.dashj.platform.dapiclient.model.GetStatusResponse;
import org.dashj.platform.dapiclient.provider.DAPIAddress;
import org.dashj.platform.dashpay.BlockchainIdentity;
import org.dashj.platform.dashpay.Contact;
import org.dashj.platform.dashpay.ContactRequest;
import org.dashj.platform.dashpay.ContactRequests;
import org.dashj.platform.dashpay.DashPayWalletExtension;
import org.dashj.platform.dashpay.Profile;
import org.dashj.platform.dashpay.RetryDelayType;
import org.dashj.platform.dashpay.callback.RegisterIdentityCallback;
import org.dashj.platform.dashpay.callback.RegisterNameCallback;
import org.dashj.platform.dashpay.callback.RegisterPreorderCallback;
import org.dashj.platform.dpp.document.Document;
import org.dashj.platform.dpp.identifier.Identifier;
import org.dashj.platform.dpp.identity.Identity;
import org.dashj.platform.sdk.platform.DomainDocument;
import org.dashj.platform.sdk.platform.Names;
import org.dashj.platform.sdk.platform.Platform;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Thread.sleep;
import static org.bitcoinj.core.Coin.parseCoin;

/**
 * A command line tool for manipulating wallets and working with Bitcoin.
 */
public class WalletTool {
    private static final Logger log = LoggerFactory.getLogger(WalletTool.class);
    private static final BaseEncoding HEX = BaseEncoding.base16().lowerCase();

    private static OptionSet options;
    private static OptionSpec<Date> dateFlag;
    private static OptionSpec<Long> unixtimeFlag;
    private static OptionSpec<String> seedFlag, watchFlag;
    private static OptionSpec<ScriptType> outputScriptTypeFlag;
    private static OptionSpec<String> xpubkeysFlag;
    private static OptionSpec<String> mixAmountFlag;
    private static OptionSpec<Integer> sessionsFlag;
    private static OptionSpec<Integer> roundsFlag;
    private static OptionSpec<Boolean> multiSessionFlag;

    private static Context context;
    private static NetworkParameters params;
    private static File walletFile;
    private static BlockStore store;
    private static AbstractBlockChain chain;
    private static PeerGroup peerGroup;
    private static WalletEx wallet;
    private static File chainFileName;
    private static ValidationMode mode;
    private static String password;
    private static org.bitcoin.protocols.payments.Protos.PaymentRequest paymentRequest;
    private static boolean isFormatCSV;
    private static File outputFile;

    private static DashPayWalletExtension dashPayWalletExtension;
    private static Platform platform;
    private static BlockchainIdentity blockchainIdentity = null;
    private static DashPayWallet dashPayWallet = null;


    public static class Condition {
        public enum Type {
            // Less than, greater than, less than or equal, greater than or equal.
            EQUAL, LT, GT, LTE, GTE
        }
        Type type;
        String value;

        public Condition(String from) {
            if (from.length() < 2) throw new RuntimeException("Condition string too short: " + from);

            if (from.startsWith("<=")) type = Type.LTE;
            else if (from.startsWith(">=")) type = Type.GTE;
            else if (from.startsWith("<")) type = Type.LT;
            else if (from.startsWith("=")) type = Type.EQUAL;
            else if (from.startsWith(">")) type = Type.GT;
            else throw new RuntimeException("Unknown operator in condition: " + from);

            String s;
            switch (type) {
                case LT:
                case GT:
                case EQUAL:
                    s = from.substring(1);
                    break;
                case LTE:
                case GTE:
                    s = from.substring(2);
                    break;
                default:
                    throw new RuntimeException("Unreachable");
            }
            value = s;
        }

        public boolean matchBitcoins(Coin comparison) {
            try {
                Coin units = parseCoin(value);
                switch (type) {
                    case LT: return comparison.compareTo(units) < 0;
                    case GT: return comparison.compareTo(units) > 0;
                    case EQUAL: return comparison.compareTo(units) == 0;
                    case LTE: return comparison.compareTo(units) <= 0;
                    case GTE: return comparison.compareTo(units) >= 0;
                    default:
                        throw new RuntimeException("Unreachable");
                }
            } catch (NumberFormatException e) {
                System.err.println("Could not parse value from condition string: " + value);
                System.exit(1);
                return false;
            }
        }
    }

    private static Condition condition;

    public enum ActionEnum {
        DUMP,
        RAW_DUMP,
        CREATE,
        ADD_KEY,
        ADD_ADDR,
        DELETE_KEY,
        CURRENT_RECEIVE_ADDR,
        SYNC,
        RESET,
        SEND,
        SEND_CLTVPAYMENTCHANNEL,
        SETTLE_CLTVPAYMENTCHANNEL,
        REFUND_CLTVPAYMENTCHANNEL,
        ENCRYPT,
        DECRYPT,
        MARRY,
        UPGRADE,
        ROTATE,
        SET_CREATION_TIME,
        DUMP_DASHPAY,
        CREATE_USERNAME,
        STATUS,
	    MIX,
    }

    public enum WaitForEnum {
        EVER,
        WALLET_TX,
        BLOCK,
        BALANCE
    }

    public enum ValidationMode {
        FULL,
        SPV
    }

    public static void main(String[] args) throws Exception {
        OptionParser parser = new OptionParser();
        parser.accepts("help");
        parser.accepts("force");
        parser.accepts("debuglog");
        OptionSpec<String> walletFileName = parser.accepts("wallet").withRequiredArg().defaultsTo("wallet");
        seedFlag = parser.accepts("seed").withRequiredArg();
        watchFlag = parser.accepts("watchkey").withRequiredArg();
        outputScriptTypeFlag = parser.accepts("output-script-type").withRequiredArg().ofType(ScriptType.class)
                .defaultsTo(ScriptType.P2PKH);
        OptionSpec<NetworkEnum> netFlag = parser.accepts("net").withRequiredArg().ofType(NetworkEnum.class).defaultsTo(NetworkEnum.MAIN);
        dateFlag = parser.accepts("date").withRequiredArg().ofType(Date.class)
                .withValuesConvertedBy(DateConverter.datePattern("yyyy/MM/dd"));
        OptionSpec<WaitForEnum> waitForFlag = parser.accepts("waitfor").withRequiredArg().ofType(WaitForEnum.class);
        OptionSpec<ValidationMode> modeFlag = parser.accepts("mode").withRequiredArg().ofType(ValidationMode.class)
                .defaultsTo(ValidationMode.SPV);
        OptionSpec<String> chainFlag = parser.accepts("chain").withRequiredArg();
        parser.accepts("csv");
        OptionSpec<String> outputFileFlag = parser.accepts("outfile").withRequiredArg();

        // For addkey/delkey.
        parser.accepts("pubkey").withRequiredArg();
        parser.accepts("privkey").withRequiredArg();
        parser.accepts("addr").withRequiredArg();
        parser.accepts("peers").withRequiredArg();
        xpubkeysFlag = parser.accepts("xpubkeys").withRequiredArg();
        OptionSpec<String> outputFlag = parser.accepts("output").withRequiredArg();
        parser.accepts("value").withRequiredArg();
        OptionSpec<String> feePerKbOption = parser.accepts("fee-per-kb").withRequiredArg();
        OptionSpec<String> feeSatPerByteOption = parser.accepts("fee-sat-per-byte").withRequiredArg();
        unixtimeFlag = parser.accepts("unixtime").withRequiredArg().ofType(Long.class);
        OptionSpec<String> conditionFlag = parser.accepts("condition").withRequiredArg();
        parser.accepts("locktime").withRequiredArg();
        parser.accepts("allow-unconfirmed");
        parser.accepts("coinjoin");
        parser.accepts("offline");
        parser.accepts("ignore-mandatory-extensions");
        OptionSpec<String> passwordFlag = parser.accepts("password").withRequiredArg();
        OptionSpec<String> paymentRequestLocation = parser.accepts("payment-request").withRequiredArg();
        parser.accepts("no-pki");
        parser.accepts("dump-privkeys");
        parser.accepts("dump-lookahead");
        OptionSpec<String> refundFlag = parser.accepts("refund-to").withRequiredArg();
        OptionSpec<String> txHashFlag = parser.accepts("txhash").withRequiredArg();

        // username
        OptionSpec<String> usernameFlag = parser.accepts("username").withRequiredArg();

        // coinjoin
        mixAmountFlag = parser.accepts("amount").withRequiredArg();
        sessionsFlag = parser.accepts("sessions").withRequiredArg().ofType(Integer.class);
        roundsFlag = parser.accepts("rounds").withRequiredArg().ofType(Integer.class);
        multiSessionFlag = parser.accepts("multi-session").withOptionalArg().ofType(Boolean.class);

        options = parser.parse(args);

        if (args.length == 0 || options.has("help") ||
                options.nonOptionArguments().size() < 1 || options.nonOptionArguments().contains("help")) {
            System.out.println(Resources.toString(WalletTool.class.getResource("wallet-tool-help.txt"), StandardCharsets.UTF_8));
            return;
        }

        ActionEnum action;
        try {
            String actionStr = (String) options.nonOptionArguments().get(0);
            actionStr = actionStr.toUpperCase().replace("-", "_");
            action = ActionEnum.valueOf(actionStr);
        } catch (IllegalArgumentException e) {
            System.err.println("Could not understand action name " + options.nonOptionArguments().get(0));
            return;
        }

        if (options.has("debuglog")) {
            BriefLogFormatter.initVerbose();
            log.info("Starting up ...");
        } else {
            // Disable logspam unless there is a flag.
            java.util.logging.Logger logger = LogManager.getLogManager().getLogger("");
            logger.setLevel(Level.SEVERE);
        }

        switch (netFlag.value(options)) {
            case MAIN:
            case PROD:
                params = MainNetParams.get();
                chainFileName = new File("mainnet.spvchain");
                break;
            case DEVNET:
                params = BinTangDevNetParams.get();
                chainFileName = new File("bintang-devnet.spvchain");
                break;
            case TEST:
                params = TestNet3Params.get();
                chainFileName = new File("testnet.spvchain");
                break;
            case REGTEST:
                params = RegTestParams.get();
                chainFileName = new File("regtest.chain");
                break;
            default:
                throw new RuntimeException("Unreachable.");
        }

        context = new Context(params);
        EnumSet<MasternodeSync.SYNC_FLAGS> syncFlags = MasternodeSync.SYNC_DEFAULT_SPV;
        syncFlags.add(MasternodeSync.SYNC_FLAGS.SYNC_HEADERS_MN_LIST_FIRST);
        syncFlags.add(MasternodeSync.SYNC_FLAGS.SYNC_BLOCKS_AFTER_PREPROCESSING);
        context.initDash(true, true, syncFlags);
        context.initDashSync(".");
        Context.propagate(context);

        platform = new Platform(params);
        dashPayWalletExtension = new DashPayWalletExtension(platform);

        platform.setMasternodeListManager(context.masternodeListManager);

        mode = modeFlag.value(options);

        // Allow the user to override the name of the chain used.
        if (options.has(chainFlag)) {
            chainFileName = new File(chainFlag.value(options));
        }

        if (options.has("condition")) {
            condition = new Condition(conditionFlag.value(options));
        }

        if (options.has(passwordFlag)) {
            password = passwordFlag.value(options);
        }

        if (options.has("csv")) {
            isFormatCSV = true;
        }

        if (options.has(outputFileFlag)) {
            outputFile = new File(outputFileFlag.value(options));
        }

        walletFile = new File(walletFileName.value(options));
        if (action == ActionEnum.CREATE) {
            createWallet(options, params, walletFile);
            return;  // We're done.
        }
        if (!walletFile.exists()) {
            System.err.println("Specified wallet file " + walletFile + " does not exist. Try wallet-tool --wallet=" + walletFile + " create");
            return;
        }

        if (action == ActionEnum.RAW_DUMP) {
            // Just parse the protobuf and print, then bail out. Don't try and do a real deserialization. This is
            // useful mostly for investigating corrupted wallets.
            FileInputStream stream = new FileInputStream(walletFile);
            try {
                Protos.Wallet proto = WalletProtobufSerializer.parseToProto(stream);
                proto = attemptHexConversion(proto);
                System.out.println(proto.toString());
                return;
            } finally {
                stream.close();
            }
        }

        InputStream walletInputStream = null;
        try {
            boolean forceReset = action == ActionEnum.RESET
                || (action == ActionEnum.SYNC
                    && options.has("force"));
            WalletProtobufSerializer loader = new WalletProtobufSerializer();
            if (options.has("ignore-mandatory-extensions"))
                loader.setRequireMandatoryExtensions(false);
            walletInputStream = new BufferedInputStream(new FileInputStream(walletFile));
            WalletExtension [] extensions = new WalletExtension[1];
            extensions[0] = dashPayWalletExtension;
            wallet = (WalletEx) loader.readWallet(walletInputStream, forceReset, extensions);
            if (!wallet.getParams().equals(params)) {
                System.err.println("Wallet does not match requested network parameters: " +
                        wallet.getParams().getId() + " vs " + params.getId());
                return;
            }

            if (!wallet.getExtensions().containsKey(DashPayWalletExtension.NAME)) {
                wallet.addExtension(dashPayWalletExtension);
                dashPayWalletExtension = (DashPayWalletExtension)wallet.getExtensions().get(DashPayWalletExtension.NAME);
                //TODO: load the info
            }
        } catch (Exception e) {
            System.err.println("Failed to load wallet '" + walletFile + "': " + e.getMessage());
            e.printStackTrace();
            return;
        } finally {
            if (walletInputStream != null) {
                walletInputStream.close();
            }
        }

        // What should we do?
        switch (action) {
            case DUMP: dumpWallet(); break;
            case DUMP_DASHPAY: dumpDashPay(); break;
            case STATUS: status(); break;
            case ADD_KEY: addKey(); break;
            case ADD_ADDR: addAddr(); break;
            case DELETE_KEY: deleteKey(); break;
            case CURRENT_RECEIVE_ADDR: currentReceiveAddr(); break;
            case RESET: reset(); break;
            case SYNC: syncChain(waitForFlag); break;
            case SEND:
                if (options.has(paymentRequestLocation) && options.has(outputFlag)) {
                    System.err.println("--payment-request and --output cannot be used together.");
                    return;
                } else if (options.has(feePerKbOption) && options.has(feeSatPerByteOption)) {
                    System.err.println("--fee-per-kb and --fee-sat-per-byte cannot be used together.");
                    return;
                } else if (options.has(outputFlag)) {
                    Coin feePerKb = null;
                    if (options.has(feePerKbOption))
                        feePerKb = parseCoin((String) options.valueOf(feePerKbOption));
                    if (options.has(feeSatPerByteOption))
                        feePerKb = Coin.valueOf(Long.parseLong(options.valueOf(feeSatPerByteOption)) * 1000);
                    String lockTime = null;
                    if (options.has("locktime")) {
                        lockTime = (String) options.valueOf("locktime");
                    }
                    boolean allowUnconfirmed = options.has("allow-unconfirmed");
                    boolean isCoinJoin = options.has("coinjoin");
                    send(outputFlag.values(options), feePerKb, lockTime, allowUnconfirmed, isCoinJoin);
                } else if (options.has(paymentRequestLocation)) {
                    sendPaymentRequest(paymentRequestLocation.value(options), !options.has("no-pki"));
                } else {
                    System.err.println("You must specify a --payment-request or at least one --output=addr:value.");
                    return;
                }
                break;
            case SEND_CLTVPAYMENTCHANNEL: {
                if (options.has(feePerKbOption) && options.has(feeSatPerByteOption)) {
                    System.err.println("--fee-per-kb and --fee-sat-per-byte cannot be used together.");
                    return;
                }
                if (!options.has(outputFlag)) {
                    System.err.println("You must specify a --output=addr:value");
                    return;
                }
                Coin feePerKb = null;
                if (options.has(feePerKbOption))
                    feePerKb = parseCoin((String) options.valueOf(feePerKbOption));
                if (options.has(feeSatPerByteOption))
                    feePerKb = Coin.valueOf(Long.parseLong(options.valueOf(feeSatPerByteOption)) * 1000);
                if (!options.has("locktime")) {
                    System.err.println("You must specify a --locktime");
                    return;
                }
                String lockTime = (String) options.valueOf("locktime");
                boolean allowUnconfirmed = options.has("allow-unconfirmed");
                if (!options.has(refundFlag)) {
                    System.err.println("You must specify an address to refund money to after expiry: --refund-to=addr");
                    return;
                }
                sendCLTVPaymentChannel(refundFlag.value(options), outputFlag.value(options), feePerKb, lockTime, allowUnconfirmed);
                } break;
            case SETTLE_CLTVPAYMENTCHANNEL: {
                if (options.has(feePerKbOption) && options.has(feeSatPerByteOption)) {
                    System.err.println("--fee-per-kb and --fee-sat-per-byte cannot be used together.");
                    return;
                }
                if (!options.has(outputFlag)) {
                    System.err.println("You must specify a --output=addr:value");
                    return;
                }
                Coin feePerKb = null;
                if (options.has(feePerKbOption))
                    feePerKb = parseCoin((String) options.valueOf(feePerKbOption));
                if (options.has(feeSatPerByteOption))
                    feePerKb = Coin.valueOf(Long.parseLong(options.valueOf(feeSatPerByteOption)) * 1000);
                boolean allowUnconfirmed = options.has("allow-unconfirmed");
                if (!options.has(txHashFlag)) {
                    System.err.println("You must specify the transaction to spend: --txhash=tx-hash");
                    return;
                }
                settleCLTVPaymentChannel(txHashFlag.value(options), outputFlag.value(options), feePerKb, allowUnconfirmed);
                } break;
            case REFUND_CLTVPAYMENTCHANNEL: {
                if (options.has(feePerKbOption) && options.has(feeSatPerByteOption)) {
                    System.err.println("--fee-per-kb and --fee-sat-per-byte cannot be used together.");
                    return;
                }
                if (!options.has(outputFlag)) {
                    System.err.println("You must specify a --output=addr:value");
                    return;
                }
                Coin feePerKb = null;
                if (options.has(feePerKbOption))
                    feePerKb = parseCoin((String) options.valueOf(feePerKbOption));
                if (options.has(feeSatPerByteOption))
                    feePerKb = Coin.valueOf(Long.parseLong(options.valueOf(feeSatPerByteOption)) * 1000);
                boolean allowUnconfirmed = options.has("allow-unconfirmed");
                if (!options.has(txHashFlag)) {
                    System.err.println("You must specify the transaction to spend: --txhash=tx-hash");
                    return;
                }
                refundCLTVPaymentChannel(txHashFlag.value(options), outputFlag.value(options), feePerKb, allowUnconfirmed);
            } break;
            case ENCRYPT: encrypt(); break;
            case DECRYPT: decrypt(); break;
            case MARRY: marry(); break;
            case UPGRADE: upgrade(); break;
            case ROTATE: rotate(); break;
            case SET_CREATION_TIME: setCreationTime(); break;
            // dashpay related commands
            case CREATE_USERNAME: {
                boolean isCoinJoin = options.has("coinjoin");
                if (!options.hasArgument("username")) {
                    System.err.println("You must specify the username to create: --username=name");
                    break;
                }
                Coin credits = Coin.valueOf(15000000);
                if (options.hasArgument("amount")) {
                    credits = parseCoin(options.valueOf(mixAmountFlag));
                }
                createUsername(waitForFlag, usernameFlag.value(options), credits, isCoinJoin);
            } break;
            case MIX: mix(waitForFlag); break;
        }

        if (!wallet.isConsistent()) {
            System.err.println("************** WALLET IS INCONSISTENT *****************");
            return;
        }

        saveWallet(walletFile);

        // wait for the preblock download to be done first
        Futures.addCallback(waitAndShutdownFuture, waitAndShutdownCallback, Executors.newSingleThreadExecutor());
        if (!context.masternodeSync.syncFlags.contains(MasternodeSync.SYNC_FLAGS.SYNC_BLOCKS_AFTER_PREPROCESSING))
            waitAndShutdownFuture.set(waitForFlag);
    }

    private static void waitAndShutdown(OptionSpec<WaitForEnum> waitForFlag) throws BlockStoreException {
        if (options.has(waitForFlag)) {
            WaitForEnum value;
            try {
                value = waitForFlag.value(options);
            } catch (Exception e) {
                System.err.println("Could not understand the --waitfor flag: Valid options are WALLET_TX, BLOCK, " +
                                   "BALANCE and EVER");
                return;
            }
            wait(value);
            if (!wallet.isConsistent()) {
                System.err.println("************** WALLET IS INCONSISTENT *****************");
                return;
            }
            saveWallet(walletFile);
        }
        shutdown();
    }

    private static Protos.Wallet attemptHexConversion(Protos.Wallet proto) {
        // Try to convert any raw hashes and such to textual equivalents for easier debugging. This makes it a bit
        // less "raw" but we will just abort on any errors.
        try {
            Protos.Wallet.Builder builder = proto.toBuilder();
            for (Protos.Transaction tx : builder.getTransactionList()) {
                Protos.Transaction.Builder txBuilder = tx.toBuilder();
                txBuilder.setHash(bytesToHex(txBuilder.getHash()));
                for (int i = 0; i < txBuilder.getBlockHashCount(); i++)
                    txBuilder.setBlockHash(i, bytesToHex(txBuilder.getBlockHash(i)));
                for (Protos.TransactionInput input : txBuilder.getTransactionInputList()) {
                    Protos.TransactionInput.Builder inputBuilder = input.toBuilder();
                    inputBuilder.setTransactionOutPointHash(bytesToHex(inputBuilder.getTransactionOutPointHash()));
                }
                for (Protos.TransactionOutput output : txBuilder.getTransactionOutputList()) {
                    Protos.TransactionOutput.Builder outputBuilder = output.toBuilder();
                    if (outputBuilder.hasSpentByTransactionHash())
                        outputBuilder.setSpentByTransactionHash(bytesToHex(outputBuilder.getSpentByTransactionHash()));
                }
                // TODO: keys, ip addresses etc.
            }
            return builder.build();
        } catch (Throwable throwable) {
            log.error("Failed to do hex conversion on wallet proto", throwable);
            return proto;
        }
    }

    private static ByteString bytesToHex(ByteString bytes) {
        return ByteString.copyFrom(Utils.HEX.encode(bytes.toByteArray()).getBytes());
    }

    private static void marry() {
        if (!options.has(xpubkeysFlag)) {
            throw new IllegalStateException();
        }

        String[] xpubkeys = options.valueOf(xpubkeysFlag).split(",");
        ImmutableList.Builder<DeterministicKey> keys = ImmutableList.builder();
        for (String xpubkey : xpubkeys) {
            keys.add(DeterministicKey.deserializeB58(null, xpubkey.trim(), params));
        }
        MarriedKeyChain chain = MarriedKeyChain.builder()
                .random(new SecureRandom())
                .followingKeys(keys.build())
                .build();
        wallet.addAndActivateHDChain(chain);
    }

    private static void upgrade() {
        DeterministicKeyChain activeKeyChain = wallet.getActiveKeyChain();
        ScriptType currentOutputScriptType = activeKeyChain != null ? activeKeyChain.getOutputScriptType() : null;
        ScriptType outputScriptType = options.valueOf(outputScriptTypeFlag);
        if (!wallet.isDeterministicUpgradeRequired(outputScriptType)) {
            System.err
                    .println("No upgrade from " + (currentOutputScriptType != null ? currentOutputScriptType : "basic")
                            + " to " + outputScriptType);
            return;
        }
        KeyParameter aesKey = null;
        if (wallet.isEncrypted()) {
            aesKey = passwordToKey(true);
            if (aesKey == null)
                return;
        }
        wallet.upgradeToDeterministic(outputScriptType, aesKey);
        System.out.println("Upgraded from " + (currentOutputScriptType != null ? currentOutputScriptType : "basic")
                + " to " + outputScriptType);
    }

    private static void rotate() throws BlockStoreException {
        setup();
        peerGroup.start();
        // Set a key rotation time and possibly broadcast the resulting maintenance transactions.
        long rotationTimeSecs = Utils.currentTimeSeconds();
        if (options.has(dateFlag)) {
            rotationTimeSecs = options.valueOf(dateFlag).getTime() / 1000;
        } else if (options.has(unixtimeFlag)) {
            rotationTimeSecs = options.valueOf(unixtimeFlag);
        }
        log.info("Setting wallet key rotation time to {}", rotationTimeSecs);
        wallet.setKeyRotationTime(rotationTimeSecs);
        KeyParameter aesKey = null;
        if (wallet.isEncrypted()) {
            aesKey = passwordToKey(true);
            if (aesKey == null)
                return;
        }
        Futures.getUnchecked(wallet.doMaintenance(aesKey, true));
    }

    private static void encrypt() {
        if (password == null) {
            System.err.println("You must provide a --password");
            return;
        }
        if (wallet.isEncrypted()) {
            System.err.println("This wallet is already encrypted.");
            return;
        }
        wallet.encrypt(password);
    }

    private static void decrypt() {
        if (password == null) {
            System.err.println("You must provide a --password");
            return;
        }
        if (!wallet.isEncrypted()) {
            System.err.println("This wallet is not encrypted.");
            return;
        }
        try {
            wallet.decrypt(password);
        } catch (KeyCrypterException e) {
            System.err.println("Password incorrect.");
        }
    }

    private static void addAddr() {
        String addr = (String) options.valueOf("addr");
        if (addr == null) {
            System.err.println("You must specify an --addr to watch.");
            return;
        }
        try {
            Address address = Address.fromBase58(params, addr);
            // If no creation time is specified, assume genesis (zero).
            wallet.addWatchedAddress(address, getCreationTimeSeconds());
        } catch (AddressFormatException e) {
            System.err.println("Could not parse given address, or wrong network: " + addr);
        }
    }

    private static void send(List<String> outputs, Coin feePerKb, String lockTimeStr, boolean allowUnconfirmed, boolean isCoinJoin) throws VerificationException {
        try {
            // Convert the input strings to outputs.
            Transaction t = new Transaction(params);
            for (String spec : outputs) {
                try {
                    OutputSpec outputSpec = new OutputSpec(spec, isCoinJoin);
                    if (outputSpec.isAddress()) {
                        t.addOutput(outputSpec.value, outputSpec.addr);
                    } else {
                        t.addOutput(outputSpec.value, outputSpec.key);
                    }
                } catch (AddressFormatException.WrongNetwork e) {
                    System.err.println("Malformed output specification, address is for a different network: " + spec);
                    return;
                } catch (AddressFormatException e) {
                    System.err.println("Malformed output specification, could not parse as address: " + spec);
                    return;
                } catch (NumberFormatException e) {
                    System.err.println("Malformed output specification, could not parse as value: " + spec);
                    return;
                } catch (IllegalArgumentException e) {
                    System.err.println(e.getMessage());
                    return;
                }
            }
            SendRequest req = isCoinJoin ? CoinJoinSendRequest.forTx(wallet, t) : SendRequest.forTx(t);
            if (isCoinJoin)
                req.coinSelector = UnmixedZeroConfCoinSelector.get();
            if (t.getOutputs().size() == 1 && t.getOutput(0).getValue().equals(isCoinJoin ? wallet.getBalance(BalanceType.COINJOIN) :wallet.getBalance())) {
                log.info("Emptying out wallet, recipient may get less than what you expect");
                req.emptyWallet = true;
            }
            if (feePerKb != null)
                req.feePerKb = feePerKb;
            if (allowUnconfirmed) {
                wallet.allowSpendingUnconfirmedTransactions();
            }
            if (password != null) {
                req.aesKey = passwordToKey(true);
                if (req.aesKey == null)
                    return;  // Error message already printed.
            }
            wallet.completeTx(req);

            try {
                if (lockTimeStr != null) {
                    t.setLockTime(parseLockTimeStr(lockTimeStr));
                    // For lock times to take effect, at least one output must have a non-final sequence number.
                    t.getInputs().get(0).setSequenceNumber(0);
                    // And because we modified the transaction after it was completed, we must re-sign the inputs.
                    wallet.signTransaction(req);
                }
            } catch (ParseException e) {
                System.err.println("Could not understand --locktime of " + lockTimeStr);
                return;
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }
            t = req.tx;   // Not strictly required today.
            System.out.println(t.getTxId());
            if (options.has("offline")) {
                wallet.commitTx(t);
                return;
            }

            setup();
            peerGroup.start();
            // Wait for peers to connect, the tx to be sent to one of them and for it to be propagated across the
            // network. Once propagation is complete and we heard the transaction back from all our peers, it will
            // be committed to the wallet.
            peerGroup.broadcastTransaction(t).future().get();
            // Hack for regtest/single peer mode, as we're about to shut down and won't get an ACK from the remote end.
            List<Peer> peerList = peerGroup.getConnectedPeers();
            if (peerList.size() == 1)
                peerList.get(0).ping().get();
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        } catch (KeyCrypterException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InsufficientMoneyException e) {
            BalanceType type = isCoinJoin ? BalanceType.COINJOIN_SPENDABLE : BalanceType.AVAILABLE_SPENDABLE;
            System.err.println("Insufficient funds: have " + wallet.getBalance(type).toFriendlyString());
        }
    }

    static class OutputSpec {
        public final Coin value;
        public final Address addr;
        public final ECKey key;

        public OutputSpec(String spec) {
            this(spec, false);
        }

        public OutputSpec(String spec, boolean isCoinJoin) throws IllegalArgumentException {
            String[] parts = spec.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Malformed output specification, must have two parts separated by :");
            }
            String destination = parts[0];
            if ("ALL".equalsIgnoreCase(parts[1]))
                value = isCoinJoin ? wallet.getBalance(BalanceType.COINJOIN) : wallet.getBalance(BalanceType.ESTIMATED);
            else
                value = parseCoin(parts[1]);
            if (destination.startsWith("0")) {
                // Treat as a raw public key.
                byte[] pubKey = new BigInteger(destination, 16).toByteArray();
                key = ECKey.fromPublicOnly(pubKey);
                addr = null;
            } else {
                // Treat as an address.
                addr = (Address)Address.fromString(params, destination);
                key = null;
            }
        }

        public boolean isAddress() {
            return addr != null;
        }
    }

    private static void sendCLTVPaymentChannel(String refund, String output, Coin feePerKb, String lockTimeStr, boolean allowUnconfirmed) throws VerificationException {
        try {
            // Convert the input strings to outputs.
            ECKey outputKey, refundKey;
            Coin value;
            try {
                OutputSpec outputSpec = new OutputSpec(output);
                if (outputSpec.isAddress()) {
                    System.err.println("Output specification must be a public key");
                    return;
                }
                outputKey = outputSpec.key;
                value = outputSpec.value;
                byte[] refundPubKey = new BigInteger(refund, 16).toByteArray();
                refundKey = ECKey.fromPublicOnly(refundPubKey);
            } catch (AddressFormatException.WrongNetwork e) {
                System.err.println("Malformed output specification, address is for a different network.");
                return;
            } catch (AddressFormatException e) {
                System.err.println("Malformed output specification, could not parse as address.");
                return;
            } catch (NumberFormatException e) {
                System.err.println("Malformed output specification, could not parse as value.");
                return;
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
                return;
            }

            long lockTime;
            try {
                lockTime = parseLockTimeStr(lockTimeStr);
            } catch (ParseException e) {
                System.err.println("Could not understand --locktime of " + lockTimeStr);
                return;
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }

            SendRequest req = SendRequest.toCLTVPaymentChannel(params, BigInteger.valueOf(lockTime), refundKey, outputKey, value);
            if (req.tx.getOutputs().size() == 1 && req.tx.getOutput(0).getValue().equals(wallet.getBalance())) {
                log.info("Emptying out wallet, recipient may get less than what you expect");
                req.emptyWallet = true;
            }
            if (feePerKb != null)
                req.feePerKb = feePerKb;
            if (allowUnconfirmed) {
                wallet.allowSpendingUnconfirmedTransactions();
            }
            if (password != null) {
                req.aesKey = passwordToKey(true);
                if (req.aesKey == null)
                    return;  // Error message already printed.
            }
            wallet.completeTx(req);

            System.out.println(req.tx.getTxId());
            if (options.has("offline")) {
                wallet.commitTx(req.tx);
                return;
            }

            setup();
            peerGroup.start();
            // Wait for peers to connect, the tx to be sent to one of them and for it to be propagated across the
            // network. Once propagation is complete and we heard the transaction back from all our peers, it will
            // be committed to the wallet.
            peerGroup.broadcastTransaction(req.tx).future().get();
            // Hack for regtest/single peer mode, as we're about to shut down and won't get an ACK from the remote end.
            List<Peer> peerList = peerGroup.getConnectedPeers();
            if (peerList.size() == 1)
                peerList.get(0).ping().get();
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        } catch (KeyCrypterException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InsufficientMoneyException e) {
            System.err.println("Insufficient funds: have " + wallet.getBalance().toFriendlyString());
        }
    }

    /**
     * Settles a CLTV payment channel transaction given that we own both private keys (ie. for testing).
     */
    private static void settleCLTVPaymentChannel(String txHash, String output, Coin feePerKb, boolean allowUnconfirmed) {
        try {
            OutputSpec outputSpec;
            Coin value;
            try {
                outputSpec = new OutputSpec(output);
                value = outputSpec.value;
            } catch (AddressFormatException.WrongNetwork e) {
                System.err.println("Malformed output specification, address is for a different network.");
                return;
            } catch (AddressFormatException e) {
                System.err.println("Malformed output specification, could not parse as address.");
                return;
            } catch (NumberFormatException e) {
                System.err.println("Malformed output specification, could not parse as value.");
                return;
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
                return;
            }

            SendRequest req = outputSpec.isAddress() ?
                    SendRequest.to(outputSpec.addr, value) :
                    SendRequest.to(params, outputSpec.key, value);
            if (feePerKb != null)
                req.feePerKb = feePerKb;

            Transaction lockTimeVerify = wallet.getTransaction(Sha256Hash.wrap(txHash));
            if (lockTimeVerify == null) {
                System.err.println("Couldn't find transaction with given hash");
                return;
            }
            TransactionOutput lockTimeVerifyOutput = null;
            for (TransactionOutput out : lockTimeVerify.getOutputs()) {
                if (ScriptPattern.isSentToCltvPaymentChannel(out.getScriptPubKey())) {
                    lockTimeVerifyOutput = out;
                }
            }
            if (lockTimeVerifyOutput == null) {
                System.err.println("TX to spend wasn't sent to LockTimeVerify");
                return;
            }

            if (!value.equals(lockTimeVerifyOutput.getValue())) {
                System.err.println("You must spend all the money in the input transaction");
            }

            if (allowUnconfirmed) {
                wallet.allowSpendingUnconfirmedTransactions();
            }
            if (password != null) {
                req.aesKey = passwordToKey(true);
                if (req.aesKey == null)
                    return;  // Error message already printed.
            }

            ECKey key1 = wallet.findKeyFromPubKey(
                    ScriptPattern.extractSenderPubKeyFromCltvPaymentChannel(lockTimeVerifyOutput.getScriptPubKey()));
            ECKey key2 = wallet.findKeyFromPubKey(
                    ScriptPattern.extractRecipientPubKeyFromCltvPaymentChannel(lockTimeVerifyOutput.getScriptPubKey()));
            if (key1 == null || key2 == null) {
                System.err.println("Don't own private keys for both pubkeys");
                return;
            }

            TransactionInput input = new TransactionInput(
                    params, req.tx, new byte[] {}, lockTimeVerifyOutput.getOutPointFor());
            req.tx.addInput(input);
            TransactionSignature sig1 =
                    req.tx.calculateSignature(0, key1, lockTimeVerifyOutput.getScriptPubKey(), Transaction.SigHash.SINGLE, false);
            TransactionSignature sig2 =
                    req.tx.calculateSignature(0, key2, lockTimeVerifyOutput.getScriptPubKey(), Transaction.SigHash.SINGLE, false);
            input.setScriptSig(ScriptBuilder.createCLTVPaymentChannelInput(sig1, sig2));

            System.out.println(req.tx.getTxId());
            if (options.has("offline")) {
                wallet.commitTx(req.tx);
                return;
            }

            setup();
            peerGroup.start();
            // Wait for peers to connect, the tx to be sent to one of them and for it to be propagated across the
            // network. Once propagation is complete and we heard the transaction back from all our peers, it will
            // be committed to the wallet.
            peerGroup.broadcastTransaction(req.tx).future().get();
            // Hack for regtest/single peer mode, as we're about to shut down and won't get an ACK from the remote end.
            List<Peer> peerList = peerGroup.getConnectedPeers();
            if (peerList.size() == 1)
                peerList.get(0).ping().get();
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        } catch (KeyCrypterException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Refunds a CLTV payment channel transaction after the lock time has expired.
     */
    private static void refundCLTVPaymentChannel(String txHash, String output, Coin feePerKb, boolean allowUnconfirmed) {
        try {
            OutputSpec outputSpec;
            Coin value;
            try {
                outputSpec = new OutputSpec(output);
                value = outputSpec.value;
            } catch (AddressFormatException.WrongNetwork e) {
                System.err.println("Malformed output specification, address is for a different network.");
                return;
            } catch (AddressFormatException e) {
                System.err.println("Malformed output specification, could not parse as address.");
                return;
            } catch (NumberFormatException e) {
                System.err.println("Malformed output specification, could not parse as value.");
                return;
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
                return;
            }

            SendRequest req = outputSpec.isAddress() ?
                    SendRequest.to(outputSpec.addr, value) :
                    SendRequest.to(params, outputSpec.key, value);
            if (feePerKb != null)
                req.feePerKb = feePerKb;

            Transaction lockTimeVerify = wallet.getTransaction(Sha256Hash.wrap(txHash));
            if (lockTimeVerify == null) {
                System.err.println("Couldn't find transaction with given hash");
                return;
            }
            TransactionOutput lockTimeVerifyOutput = null;
            for (TransactionOutput out : lockTimeVerify.getOutputs()) {
                if (ScriptPattern.isSentToCltvPaymentChannel(out.getScriptPubKey())) {
                    lockTimeVerifyOutput = out;
                }
            }
            if (lockTimeVerifyOutput == null) {
                System.err.println("TX to spend wasn't sent to LockTimeVerify");
                return;
            }

            req.tx.setLockTime(ScriptPattern.extractExpiryFromCltvPaymentChannel(lockTimeVerifyOutput.getScriptPubKey()).longValue());

            if (!value.equals(lockTimeVerifyOutput.getValue())) {
                System.err.println("You must spend all the money in the input transaction");
            }

            if (allowUnconfirmed) {
                wallet.allowSpendingUnconfirmedTransactions();
            }
            if (password != null) {
                req.aesKey = passwordToKey(true);
                if (req.aesKey == null)
                    return;  // Error message already printed.
            }

            ECKey key = wallet.findKeyFromPubKey(
                    ScriptPattern.extractSenderPubKeyFromCltvPaymentChannel(lockTimeVerifyOutput.getScriptPubKey()));
            if (key == null) {
                System.err.println("Don't own private key for pubkey");
                return;
            }

            TransactionInput input = new TransactionInput(
                    params, req.tx, new byte[] {}, lockTimeVerifyOutput.getOutPointFor());
            input.setSequenceNumber(0);
            req.tx.addInput(input);
            TransactionSignature sig =
                    req.tx.calculateSignature(0, key, lockTimeVerifyOutput.getScriptPubKey(), Transaction.SigHash.SINGLE, false);
            input.setScriptSig(ScriptBuilder.createCLTVPaymentChannelRefund(sig));

            System.out.println(req.tx.getTxId());
            if (options.has("offline")) {
                wallet.commitTx(req.tx);
                return;
            }

            setup();
            peerGroup.start();
            // Wait for peers to connect, the tx to be sent to one of them and for it to be propagated across the
            // network. Once propagation is complete and we heard the transaction back from all our peers, it will
            // be committed to the wallet.
            peerGroup.broadcastTransaction(req.tx).future().get();
            // Hack for regtest/single peer mode, as we're about to shut down and won't get an ACK from the remote end.
            List<Peer> peerList = peerGroup.getConnectedPeers();
            if (peerList.size() == 1)
                peerList.get(0).ping().get();
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        } catch (KeyCrypterException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses the string either as a whole number of blocks, or if it contains slashes as a YYYY/MM/DD format date
     * and returns the lock time in wire format.
     */
    private static long parseLockTimeStr(String lockTimeStr) throws ParseException {
        if (lockTimeStr.indexOf("/") != -1) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
            Date date = format.parse(lockTimeStr);
            return date.getTime() / 1000;
        }
        return Long.parseLong(lockTimeStr);
    }

    private static void sendPaymentRequest(String location, boolean verifyPki) {
        if (location.startsWith("http") || location.startsWith("dash")) {
            try {
                ListenableFuture<PaymentSession> future;
                if (location.startsWith("http")) {
                    future = PaymentSession.createFromUrl(location, verifyPki);
                } else {
                    BitcoinURI paymentRequestURI = new BitcoinURI(location);
                    future = PaymentSession.createFromBitcoinUri(paymentRequestURI, verifyPki);
                }
                PaymentSession session = future.get();
                if (session != null) {
                    send(session);
                } else {
                    System.err.println("Server returned null session");
                    System.exit(1);
                }
            } catch (PaymentProtocolException e) {
                System.err.println("Error creating payment session " + e.getMessage());
                System.exit(1);
            } catch (BitcoinURIParseException e) {
                System.err.println("Invalid Dash uri: " + e.getMessage());
                System.exit(1);
            } catch (InterruptedException e) {
                // Ignore.
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        } else {
            // Try to open the payment request as a file.
            FileInputStream stream = null;
            try {
                File paymentRequestFile = new File(location);
                stream = new FileInputStream(paymentRequestFile);
            } catch (Exception e) {
                System.err.println("Failed to open file: " + e.getMessage());
                System.exit(1);
            }
            try {
                paymentRequest = org.bitcoin.protocols.payments.Protos.PaymentRequest.newBuilder().mergeFrom(stream).build();
            } catch(IOException e) {
                System.err.println("Failed to parse payment request from file " + e.getMessage());
                System.exit(1);
            }
            PaymentSession session = null;
            try {
                session = new PaymentSession(paymentRequest, verifyPki);
            } catch (PaymentProtocolException e) {
                System.err.println("Error creating payment session " + e.getMessage());
                System.exit(1);
            }
            send(session);
        }
    }

    private static void send(PaymentSession session) {
        try {
            System.out.println("Payment Request");
            System.out.println("Coin: " + session.getValue().toFriendlyString());
            System.out.println("Date: " + session.getDate());
            System.out.println("Memo: " + session.getMemo());
            if (session.pkiVerificationData != null) {
                System.out.println("Pki-Verified Name: " + session.pkiVerificationData.displayName);
                System.out.println("PKI data verified by: " + session.pkiVerificationData.rootAuthorityName);
            }
            final SendRequest req = session.getSendRequest();
            if (password != null) {
                req.aesKey = passwordToKey(true);
                if (req.aesKey == null)
                    return;   // Error message already printed.
            }
            wallet.completeTx(req);  // may throw InsufficientMoneyException.
            if (options.has("offline")) {
                wallet.commitTx(req.tx);
                return;
            }
            setup();
            // No refund address specified, no user-specified memo field.
            ListenableFuture<PaymentProtocol.Ack> future = session.sendPayment(ImmutableList.of(req.tx), null, null);
            if (future == null) {
                // No payment_url for submission so, broadcast and wait.
                peerGroup.start();
                peerGroup.broadcastTransaction(req.tx).future().get();
            } else {
                PaymentProtocol.Ack ack = future.get();
                wallet.commitTx(req.tx);
                System.out.println("Memo from server: " + ack.getMemo());
            }
        } catch (PaymentProtocolException e) {
            System.err.println("Failed to send payment " + e.getMessage());
            System.exit(1);
        } catch (VerificationException e) {
            System.err.println("Failed to send payment " + e.getMessage());
            System.exit(1);
        } catch (ExecutionException e) {
            System.err.println("Failed to send payment " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Invalid payment " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e1) {
            // Ignore.
        } catch (InsufficientMoneyException e) {
            System.err.println("Insufficient funds: have " + wallet.getBalance().toFriendlyString());
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        }
    }

    private static void wait(WaitForEnum waitFor) throws BlockStoreException {
        final CountDownLatch latch = new CountDownLatch(1);
        setup();
        switch (waitFor) {
            case EVER:
                break;

            case WALLET_TX:
                wallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
                    @Override
                    public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                        // Runs in a peer thread.
                        System.out.println(tx.getTxId());
                        latch.countDown();  // Wake up main thread.
                    }
                });
                wallet.addCoinsSentEventListener(new WalletCoinsSentEventListener() {
                    @Override
                    public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                        // Runs in a peer thread.
                        System.out.println(tx.getTxId());
                        latch.countDown();  // Wake up main thread.
                    }
                });
                break;

            case BLOCK:
                peerGroup.addBlocksDownloadedEventListener(new BlocksDownloadedEventListener() {
                    @Override
                    public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
                        // Check if we already ran. This can happen if a block being received triggers download of more
                        // blocks, or if we receive another block whilst the peer group is shutting down.
                        if (latch.getCount() == 0) return;
                        System.out.println(block.getHashAsString());
                        latch.countDown();
                    }
                });
                break;

            case BALANCE:
                // Check if the balance already meets the given condition.
                if (condition.matchBitcoins(wallet.getBalance(BalanceType.ESTIMATED))) {
                    latch.countDown();
                    break;
                }
                final WalletEventListener listener = new WalletEventListener(latch);
                wallet.addCoinsReceivedEventListener(listener);
                wallet.addCoinsSentEventListener(listener);
                wallet.addChangeEventListener(listener);
                wallet.addReorganizeEventListener(listener);
                break;

        }
        if (!peerGroup.isRunning())
            peerGroup.startAsync();
        try {
            latch.await();
        } catch (InterruptedException e) {
            // Ignore.
        }
    }

    private static void reset() {
        // Delete the transactions and save. In future, reset the chain head pointer.
        wallet.clearTransactions(0);
        saveWallet(walletFile);
    }

    // Sets up all objects needed for network communication but does not bring up the peers.
    private static void setup() throws BlockStoreException { setup(true); }
    private static void setup(boolean autoSave) throws BlockStoreException {
        if (store != null) return;  // Already done.
        // Will create a fresh chain if one doesn't exist or there is an issue with this one.
        boolean reset = !chainFileName.exists();
        if (reset) {
            // No chain, so reset the wallet as we will be downloading from scratch.
            System.out.println("Chain file is missing so resetting the wallet.");
            reset();
        }
        if (mode == ValidationMode.SPV) {
            store = new SPVBlockStore(params, chainFileName);
            if (reset) {
                try {
                    CheckpointManager.checkpoint(params, CheckpointManager.openStream(params), store,
                            wallet.getEarliestKeyCreationTime());
                    StoredBlock head = store.getChainHead();
                    System.out.println("Skipped to checkpoint " + head.getHeight() + " at "
                            + Utils.dateTimeFormat(head.getHeader().getTimeSeconds() * 1000));
                } catch (IOException x) {
                    System.out.println("Could not load checkpoints: " + x.getMessage());
                }
            }
            chain = new BlockChain(params, wallet, store);
        } else if (mode == ValidationMode.FULL) {
            store = new H2FullPrunedBlockStore(params, chainFileName.getAbsolutePath(), 5000);
            chain = new FullPrunedBlockChain(params, wallet, (FullPrunedBlockStore) store);
        }
        // This will ensure the wallet is saved when it changes.
        if (autoSave)
            wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null);
        if (peerGroup == null) {
            peerGroup = new PeerGroup(params, chain);
        }
        peerGroup.setUserAgent("WalletTool", "1.0");
        if (params == RegTestParams.get())
            peerGroup.setMinBroadcastConnections(1);
        peerGroup.addWallet(wallet);
        if (options.has("peers")) {
            String peersFlag = (String) options.valueOf("peers");
            String[] peerAddrs = peersFlag.split(",");
            for (String peer : peerAddrs) {
                try {
                    peerGroup.addAddress(new PeerAddress(params, InetAddress.getByName(peer)));
                } catch (UnknownHostException e) {
                    System.err.println("Could not understand peer domain name/IP address: " + peer + ": " + e.getMessage());
                    System.exit(1);
                }
            }
        } else {
            // TODO: we used to use peerGroup.setRequiredServices(0); here
            peerGroup.addPeerDiscovery(new ThreeMethodPeerDiscovery(params, Context.get().masternodeListManager));
        }
    }

    private static void syncChain(OptionSpec<WaitForEnum> waitForFlag) {
        try {
            setup(false);
            int startTransactions = wallet.getTransactions(true).size();
            DownloadProgressTracker listener = new DownloadProgressTracker();

            //set up the sync process correctly
            peerGroup.addPreBlocksDownloadListener(Threading.SAME_THREAD, new PreBlocksDownloadListener() {
                @Override
                public void onPreBlocksDownload(Peer peer) {
                    initializeIdentity();

                    peerGroup.triggerPreBlockDownloadComplete();
                    wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null);
                    waitAndShutdownFuture.set(waitForFlag);
                }
            });

            peerGroup.start();
            peerGroup.startBlockChainDownload(listener);
            try {
                listener.await();
            } catch (InterruptedException e) {
                System.err.println("Chain download interrupted, quitting ...");
                System.exit(1);
            }
            int endTransactions = wallet.getTransactions(true).size();
            if (endTransactions > startTransactions) {
                System.out.println("Synced " + (endTransactions - startTransactions) + " transactions.");
            }
        } catch (BlockStoreException e) {
            System.err.println("Error reading block chain file " + chainFileName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void initializeIdentity() {
        // Determine our blockchain identity
        blockchainIdentity = dashPayWalletExtension.getBlockchainIdentity();
        if (blockchainIdentity == null) {
            List<CreditFundingTransaction> cftxs = wallet.getIdentityFundingTransactions();
            if (!cftxs.isEmpty()) {
                CreditFundingTransaction cftx = cftxs.get(0);
                blockchainIdentity = new BlockchainIdentity(platform, 0, wallet);
                dashPayWalletExtension.setBlockchainIdentity(blockchainIdentity);
                if (!blockchainIdentity.recoverIdentity(cftx)) {
                    blockchainIdentity.initializeCreditFundingTransaction(cftxs.get(0));
                }
            } else {
                byte [] pubKeyHash = wallet.getBlockchainIdentityKeyChain().getWatchingKey().getPubKeyHash();
                Identity identity = platform.getIdentities().getByPublicKeyHash(pubKeyHash);
                if (identity != null) {
                    blockchainIdentity = new BlockchainIdentity(platform, 0, wallet);
                    dashPayWalletExtension.setBlockchainIdentity(blockchainIdentity);
                    blockchainIdentity.recoverIdentity(pubKeyHash);

                }
            }
        }

        if (blockchainIdentity != null && blockchainIdentity.getCurrentUsername() == null && blockchainIdentity.registrationStatus == BlockchainIdentity.RegistrationStatus.REGISTERED) {
            blockchainIdentity.recoverUsernames();
        }

        if (blockchainIdentity != null && blockchainIdentity.getCurrentUsername() != null) {
            // synchronize the Platform data here
            dashPayWallet = new DashPayWallet(blockchainIdentity, peerGroup, null);
            dashPayWallet.updateContactRequests();
        } else {
            //throw new RuntimeException("blockchainIdentity is null");
            // there is no identity
        }

        try {
            wallet.saveToFile(walletFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void shutdown() {
        try {
            if (peerGroup == null) return;  // setup() never called so nothing to do.
            if (peerGroup.isRunning())
                peerGroup.stop();
            saveWallet(walletFile);
            store.close();
            wallet.getContext().close();
            wallet = null;
            System.exit(0);
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createWallet(OptionSet options, NetworkParameters params, File walletFile) throws IOException {
        if (walletFile.exists() && !options.has("force")) {
            System.err.println("Wallet creation requested but " + walletFile + " already exists, use --force");
            return;
        }
        long creationTimeSecs = getCreationTimeSeconds();
        ScriptType outputScriptType = options.valueOf(outputScriptTypeFlag);
        if (creationTimeSecs == 0)
            creationTimeSecs = MnemonicCode.BIP39_STANDARDISATION_TIME_SECS;
        if (options.has(seedFlag)) {
            String seedStr = options.valueOf(seedFlag);
            DeterministicSeed seed;
            // Parse as mnemonic code.
            final List<String> split = ImmutableList
                    .copyOf(Splitter.on(CharMatcher.anyOf(" :;,")).omitEmptyStrings().split(seedStr));
            String passphrase = ""; // TODO allow user to specify a passphrase
            seed = new DeterministicSeed(split, null, passphrase, creationTimeSecs);
            try {
                seed.check();
            } catch (MnemonicException.MnemonicLengthException e) {
                System.err.println("The seed did not have 12 words in, perhaps you need quotes around it?");
                return;
            } catch (MnemonicException.MnemonicWordException e) {
                System.err.println("The seed contained an unrecognised word: " + e.badWord);
                return;
            } catch (MnemonicException.MnemonicChecksumException e) {
                System.err.println("The seed did not pass checksumming, perhaps one of the words is wrong?");
                return;
            } catch (MnemonicException e) {
                // not reached - all subclasses handled above
                throw new RuntimeException(e);
            }
            wallet = WalletEx.fromSeed(params, seed, outputScriptType);
        } else if (options.has(watchFlag)) {
            wallet = WalletEx.fromWatchingKeyB58(params, options.valueOf(watchFlag), creationTimeSecs);
        } else {
            wallet = WalletEx.createDeterministic(params, outputScriptType);
        }
        // add BIP44 key chain
        // BIP44 Chain
        DeterministicKeyChain bip44 = DeterministicKeyChain.builder()
                .seed(wallet.getKeyChainSeed())
                .accountPath(DerivationPathFactory.get(params).bip44DerivationPath(0))
                .build();
        wallet.addAndActivateHDChain(bip44);
        wallet.initializeAuthenticationKeyChains(wallet.getKeyChainSeed(), null);
        if (password != null)
            wallet.encrypt(password);
        wallet.saveToFile(walletFile);
    }

    private static void saveWallet(File walletFile) {
        try {
            // This will save the new state of the wallet to a temp file then rename, in case anything goes wrong.
            wallet.saveToFile(walletFile);
        } catch (IOException e) {
            System.err.println("Failed to save wallet! Old wallet should be left untouched.");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void addKey() {
        ECKey key;
        long creationTimeSeconds = getCreationTimeSeconds();
        if (options.has("privkey")) {
            String data = (String) options.valueOf("privkey");
            try {
                DumpedPrivateKey dpk = DumpedPrivateKey.fromBase58(params, data); // WIF
                key = dpk.getKey();
            } catch (AddressFormatException e) {
                byte[] decode = parseAsHexOrBase58(data);
                if (decode == null) {
                    System.err.println("Could not understand --privkey as either WIF, hex or base58: " + data);
                    return;
                }
                key = ECKey.fromPrivate(new BigInteger(1, decode));
            }
            if (options.has("pubkey")) {
                // Give the user a hint.
                System.out.println("You don't have to specify --pubkey when a private key is supplied.");
            }
            key.setCreationTimeSeconds(creationTimeSeconds);
        } else if (options.has("pubkey")) {
            byte[] pubkey = parseAsHexOrBase58((String) options.valueOf("pubkey"));
            key = ECKey.fromPublicOnly(pubkey);
            key.setCreationTimeSeconds(creationTimeSeconds);
        } else {
            System.err.println("Either --privkey or --pubkey must be specified.");
            return;
        }
        if (wallet.hasKey(key)) {
            System.err.println("That key already exists in this wallet.");
            return;
        }
        try {
            if (wallet.isEncrypted()) {
                KeyParameter aesKey = passwordToKey(true);
                if (aesKey == null)
                    return;   // Error message already printed.
                key = key.encrypt(checkNotNull(wallet.getKeyCrypter()), aesKey);
            }
        } catch (KeyCrypterException kce) {
            System.err.println("There was an encryption related error when adding the key. The error was '"
                    + kce.getMessage() + "'.");
            return;
        }
        if (!key.isCompressed())
            System.out.println("WARNING: Importing an uncompressed key");
        wallet.importKey(key);
        System.out.println(Address.fromKey(params, key) + " " + key);
    }

    @Nullable
    private static KeyParameter passwordToKey(boolean printError) {
        if (password == null) {
            if (printError)
                System.err.println("You must provide a password.");
            return null;
        }
        if (!wallet.checkPassword(password)) {
            if (printError)
                System.err.println("The password is incorrect.");
            return null;
        }
        return checkNotNull(wallet.getKeyCrypter()).deriveKey(password);
    }

    /**
     * Attempts to parse the given string as arbitrary-length hex or base58 and then return the results, or null if
     * neither parse was successful.
     */
    private static byte[] parseAsHexOrBase58(String data) {
        try {
            return Utils.HEX.decode(data);
        } catch (Exception e) {
            // Didn't decode as hex, try base58.
            try {
                return Base58.decodeChecked(data);
            } catch (AddressFormatException e1) {
                return null;
            }
        }
    }

    private static long getCreationTimeSeconds() {
        if (options.has(unixtimeFlag))
            return unixtimeFlag.value(options);
        else if (options.has(dateFlag))
            return dateFlag.value(options).getTime() / 1000;
        else
            return 0;
    }

    private static void deleteKey() {
        String pubKey = (String) options.valueOf("pubkey");
        String addr = (String) options.valueOf("addr");
        if (pubKey == null && addr == null) {
            System.err.println("One of --pubkey or --addr must be specified.");
            return;
        }
        ECKey key = null;
        if (pubKey != null) {
            key = wallet.findKeyFromPubKey(HEX.decode(pubKey));
        } else {
            try {
                Address address = Address.fromString(wallet.getParams(), addr);
                key = wallet.findKeyFromAddress(address);
            } catch (AddressFormatException e) {
                System.err.println(addr + " does not parse as a Dash address of the right network parameters.");
                return;
            }
        }
        if (key == null) {
            System.err.println("Wallet does not seem to contain that key.");
            return;
        }
        boolean removed = wallet.removeKey(key);
        if (removed)
            System.out.println("Key " + key + " was removed");
        else
            System.err.println("Key " + key + " could not be removed");
    }

    private static void currentReceiveAddr() {
        Address address = wallet.currentReceiveAddress();
        System.out.println(address);
    }

    private static void dumpWallet() throws BlockStoreException {
        // Setup to get the chain height so we can estimate lock times, but don't wipe the transactions if it's not
        // there just for the dump case.
        if (chainFileName.exists())
            setup();

        final boolean dumpPrivkeys = options.has("dump-privkeys");
        final boolean dumpLookahead = options.has("dump-lookahead");

        if (dumpPrivkeys && wallet.isEncrypted()) {
            if (password != null) {
                final KeyParameter aesKey = passwordToKey(true);
                if (aesKey == null)
                    return; // Error message already printed.
                System.out.println(wallet.toString(dumpLookahead, true, aesKey, true, true, chain));
            } else {
                System.err.println("Can't dump privkeys, wallet is encrypted.");
                return;
            }
        } else {
            System.out.println(wallet.toString(dumpLookahead, dumpPrivkeys, null, true, true, chain));
        }
    }

    private static void mix(OptionSpec<WaitForEnum> waitForFlag) {
        wallet.getCoinJoin().addKeyChain(wallet.getKeyChainSeed(), DerivationPathFactory.get(wallet.getParams()).coinJoinDerivationPath());
        syncChain(waitForFlag);
        // set defaults
        CoinJoinReporter reporter = new CoinJoinReporter();
        CoinJoinClientOptions.setEnabled(true);
        CoinJoinClientOptions.setRounds(4);
        CoinJoinClientOptions.setSessions(1);
        Coin amountToMix = wallet.getBalance();

        // set command line arguments
        if (options.has(mixAmountFlag)) {
            amountToMix = Coin.parseCoin(options.valueOf(mixAmountFlag));
        }
        CoinJoinClientOptions.setAmount(amountToMix);

        if (options.has(sessionsFlag)) {
            CoinJoinClientOptions.setSessions(options.valueOf(sessionsFlag));
        }

        if (options.has(roundsFlag)) {
            CoinJoinClientOptions.setRounds(options.valueOf(roundsFlag));
        }
        wallet.getCoinJoin().setRounds(CoinJoinClientOptions.getRounds());

        if (options.has(multiSessionFlag)) {
            CoinJoinClientOptions.setMultiSessionEnabled(options.valueOf(multiSessionFlag));
        }

        ProTxToOutpoint.initialize(params);
        wallet.getContext().coinJoinManager.coinJoinClientManagers.put(wallet.getDescription(), new CoinJoinClientManager(wallet));
        wallet.getContext().coinJoinManager.addSessionStartedListener(Threading.SAME_THREAD, reporter);
        wallet.getContext().coinJoinManager.addSessionCompleteListener(Threading.SAME_THREAD, reporter);
        wallet.getContext().coinJoinManager.addMixingCompleteListener(Threading.SAME_THREAD, reporter);

        // mix coins
        try {
            CoinJoinClientManager it = wallet.getContext().coinJoinManager.coinJoinClientManagers.get(wallet.getDescription());
            it.setStopOnNothingToDo(true);
            it.setBlockChain(wallet.getContext().blockChain);

            {
                if (wallet.isEncrypted()) {
                    // we need to handle encrypted wallets
                    System.out.print("Error: Please unlock wallet for mixing with walletpassphrase first.");
                    return;
                }
            }

            if (!it.startMixing()) {
                System.out.println("Mixing has been started already.");
                return;
            }

            boolean result = it.doAutomaticDenominating();
            System.out.println("Mixing " + (result ? "started successfully" : ("start failed: " + it.getStatuses() + ", will retry")));

            // wait until finished mixing
            SettableFuture<Boolean> mixingFinished = wallet.getContext().coinJoinManager.getMixingFinishedFuture(wallet);
            mixingFinished.addListener(() -> System.out.println("Mixing complete."), Threading.SAME_THREAD);
            mixingFinished.get();
            wallet.getContext().coinJoinManager.removeSessionCompleteListener(reporter);
            wallet.getContext().coinJoinManager.removeMixingCompleteListener(reporter);
            wallet.getContext().coinJoinManager.removeSessionStartedListener(reporter);
            wallet.getContext().coinJoinManager.stop();
        } catch (ExecutionException | InterruptedException x) {
            throw new RuntimeException(x);
        }
    }




    private static void outputToCSV(String value, @Nullable FileOutputStream stream) {
        if (stream != null) {
            try {
                stream.write((value + ",").getBytes());
            } catch (IOException x) {
                throw new RuntimeException(x);
            }
        }
    }

    private static void dumpDashPay() {
        PrintStream outputStream = System.out;
        FileOutputStream csvFile = null;
        boolean wasUpgraded = false;
        try {
            try {
                initializeIdentity();
            } catch (IllegalStateException x) {
                wasUpgraded = true;
            }

            if (outputFile != null && !isFormatCSV) {
                try {
                    outputStream = new PrintStream(outputFile);
                } catch (FileNotFoundException x) {
                    throw new RuntimeException(x);
                }
            } else if (isFormatCSV) {
                try {
                    csvFile = new FileOutputStream(outputFile, true);
                } catch (FileNotFoundException x) {
                    throw new RuntimeException(x);
                }
            }

            outputStream.println("DashPay Activity Report");
            outputStream.println("-----------------------------------------------");
            List<String> wordList = wallet.getKeyChainSeed().getMnemonicCode();
            StringBuilder words = new StringBuilder();
            for (String word : wordList) {
                words.append(word).append(" ");
            }
            String wordsString = words.toString().trim();
            outputStream.println("Recovery Phrase:                  " + wordsString);
            outputToCSV(wordsString, csvFile);
            if (blockchainIdentity == null) {
                outputStream.println("Username: Not registered");
                outputToCSV(wasUpgraded ? "registered with upgraded wallet" : "not registered", csvFile);
                return;
            }

            String username = blockchainIdentity.getCurrentUsername() == null ? (wasUpgraded ? "registered with upgraded wallet" : "not registered") : blockchainIdentity.getCurrentUsername();
            outputStream.println("Username:                         " + username);
            outputToCSV(username, csvFile);
            Profile profile = dashPayWallet.getProfiles().get(blockchainIdentity.getUniqueIdentifier());
            String displayName = profile != null ? profile.getDisplayName() : "No profile created";
            outputStream.println("Display Name:                     " + displayName);
            outputToCSV(displayName, csvFile);

            DateFormat dateFormat = DateFormat.getDateTimeInstance();
            if (wallet.getCreditFundingTransactions().size() > 0) {
                CreditFundingTransaction cftx = wallet.getCreditFundingTransactions().get(0);
                Transaction tx = wallet.getTransaction(cftx.getTxId());
                String date = "\"" + dateFormat.format(tx.getUpdateTime()) + "\"";
                outputStream.println("Username Created:                 " + date);
                outputToCSV(date, csvFile);
            } else {
                outputStream.println("Username Created:                 " + "N/A");
                outputToCSV("N/A", csvFile);
            }

            String balance = BtcAutoFormat.getCoinInstance().format(wallet.getBalance(BalanceType.ESTIMATED), 8, 4);
            outputStream.println("Balance:                          " + balance);
            outputStream.println("Balance (Credits):                " + Coin.valueOf(blockchainIdentity.getIdentity().getBalance()/1000).toFriendlyString());
            outputToCSV(balance, csvFile);

            Set<Identifier> ids = dashPayWallet.getContactIdentities();
            int inboundTx = 0, outboundTx = 0;
            Transaction firstOutboundTx = null;
            Map<Identifier, ContactRequest> received = dashPayWallet.getRecievedContactRequestsMap();
            for (Identifier id : ids) {
                ContactRequest from = received.get(id);
                int accountRef = 0;
                if (from != null) {
                    accountRef = from.getAccountReference();
                }
                List<Transaction> list = blockchainIdentity.getContactTransactions(id, accountRef);
                for (Transaction contactTx : list) {
                    if (contactTx.getValue(wallet).isPositive())
                        inboundTx++;
                    else {
                        outboundTx++;
                        if (firstOutboundTx == null) {
                            firstOutboundTx = contactTx;
                        } else {
                            if (firstOutboundTx.getUpdateTime().after(contactTx.getUpdateTime())) {
                                firstOutboundTx = contactTx;
                            }
                        }
                    }
                }
            }
            Map<Identifier, Identity> invites = blockchainIdentity.getInvitationHistory();
            int invitesClaimed = 0;
            for (Identity identity : invites.values()) {
                invitesClaimed += identity != null ? 1 : 0;
            }

            String firstOutboundDate = ((firstOutboundTx != null) ? ("\"" + dateFormat.format(firstOutboundTx.getUpdateTime()) + "\""): "N/A");
            outputStream.println("First Outbound Tx's Date/Time:    " + firstOutboundDate);
            outputStream.println("Outbound Username Tx's:           " + outboundTx);
            outputStream.println("Inbound Username Tx's:            " + inboundTx);
            outputStream.println("Outbound contact requests:        " + dashPayWallet.getSentContactRequestsMap().size());
            outputStream.println("Inbound contact requests:         " + dashPayWallet.getRecievedContactRequestsMap().size());
            outputToCSV(firstOutboundDate, csvFile);
            outputToCSV("" + outboundTx, csvFile);
            outputToCSV("" + inboundTx, csvFile);
            outputToCSV("" + dashPayWallet.getSentContactRequests().size(), csvFile);
            outputToCSV("" + dashPayWallet.getRecievedContactRequests().size(), csvFile);

            List<Contact> contacts = dashPayWallet.getEstablishedContacts();
            outputStream.println("Contacts:                         " + contacts.size());
            outputToCSV("" + contacts.size(), csvFile);
            StringBuilder contactsString = new StringBuilder();
            for (Contact contact : contacts) {
                outputStream.println("  " + contact.getUsername());
                contactsString.append(contact.getUsername() + " ");
            }
            outputToCSV(contactsString.toString(), csvFile);
            outputStream.println("Invites created:                  " + invites.size());
            outputStream.println("Invites claimed:                  " + invitesClaimed);
            outputToCSV("" + invites.size(), csvFile);
            outputToCSV("" + invitesClaimed, csvFile);
            outputStream.println();

        } finally {
            if (outputFile != null && !isFormatCSV) {
                outputStream.close();
            } else if (isFormatCSV) {
                try {
                    csvFile.write("\n".getBytes());
                    csvFile.close();
                } catch (IOException x) {

                }
            }
        }
    }

    private static void createUsername(OptionSpec<WaitForEnum> waitForFlag, String username, Coin credits, boolean useCoinJoin) {
        initializeIdentity();

        if (blockchainIdentity == null) {
            try {

                //need to mix
                if (useCoinJoin &&wallet.getCoinJoinBalance().isLessThan(credits)) {
                    mix(waitForFlag);
                }

                blockchainIdentity = new BlockchainIdentity(platform, 0, wallet);

                CreditFundingTransaction cftx = blockchainIdentity.createCreditFundingTransaction(credits, null, useCoinJoin);
                boolean wait = true;
                cftx.getConfidence().addEventListener(new TransactionConfidence.Listener() {
                    @Override
                    public void onConfidenceChanged(TransactionConfidence confidence, ChangeReason reason) {
                        // If this transaction is InstantSend Locked, then it has been sent successfully
                        switch (reason) {
                            case IX_TYPE:
                                // TODO: allow for received (IX_REQUEST) instantsend locks
                                // until the bug related to instantsend lock verification is fixed.
                                if (confidence.isTransactionLocked() || confidence.getIXType() == TransactionConfidence.IXType.IX_REQUEST) {
                                    confidence.removeEventListener(this);
                                    blockchainIdentity.setCreditFundingTransaction(cftx);
                                    System.out.println("Asset Lock Transaction has been sent: " + cftx.getTxId());
                                    createIdentity(waitForFlag, username);
                                }
                                break;
                            case DEPTH:
                                // TODO: work with chainlocks
                                break;
                            case REJECT:
                                if (confidence.hasRejections() && confidence.getRejections().size() >= 1) {
                                    confidence.removeEventListener(this);
                                    System.out.println("Error sending ${cftx.txId}: ${confidence.rejectedTransactionException.rejectMessage.reasonString}");
                                }
                                break;
                            case TYPE:
                                if (confidence.hasErrors()) {
                                    confidence.removeEventListener(this);
                                    RejectMessage.RejectCode code;
                                    switch (confidence.getConfidenceType()) {
                                        case DEAD:
                                            code = RejectMessage.RejectCode.INVALID;
                                            break;
                                        case IN_CONFLICT:
                                            code = RejectMessage.RejectCode.DUPLICATE;
                                            break;
                                        default:
                                            code = RejectMessage.RejectCode.OTHER;
                                            break;
                                    }
                                    RejectMessage rejectMessage = new RejectMessage(params, code, confidence.getTransactionHash(),
                                            "Credit funding transaction is dead or double-spent", "cftx-dead-or-double-spent");
                                    System.out.println("Error sending ${cftx.txId}: " + rejectMessage.getReasonString());
                                }

                            default:
                                // ignore
                                break;

                        }
                    }
                });
                setup();
                if (!peerGroup.isRunning())
                    peerGroup.start();
                // Wait for peers to connect, the tx to be sent to one of them and for it to be propagated across the
                // network. Once propagation is complete and we heard the transaction back from all our peers, it will
                // be committed to the wallet.
                peerGroup.broadcastTransaction(cftx).future().get();
                // Hack for regtest/single peer mode, as we're about to shut down and won't get an ACK from the remote end.
                List<Peer> peerList = peerGroup.getConnectedPeers();
                if (peerList.size() == 1)
                    peerList.get(0).ping().get();

                //while (!waitAndShutdownFuture.isDone())
                //    sleep(1000);
                sleep(10 * 60 * 1000);
            } catch (BlockStoreException x) {
                throw new RuntimeException(x);
            } catch (InterruptedException | ExecutionException x) {
                throw new RuntimeException(x);
            } catch (Exception x) {
                if (x instanceof InsufficientMoneyException) {
                    if (useCoinJoin)
                        System.out.println("There is not enough coinjoin money in the wallet: " + wallet.getBalance(BalanceType.COINJOIN));
                    else
                        System.out.println("There is not enough money in the wallet: " + wallet.getBalance() + " of " + wallet.getBalance(BalanceType.ESTIMATED_SPENDABLE));
                } else {
                    System.out.println("There was an exception while attempting to create a username: " + x.getMessage());
                    x.printStackTrace();
                }
            }
        } else {
            System.out.println("This wallet already has an identity");

            // check for username

            if (!blockchainIdentity.isRegistered()) {
                createIdentity(waitForFlag, username);
            } else {
                if (platform.getNames().get(username, Names.DEFAULT_PARENT_DOMAIN) == null) {
                    System.out.println("This wallet already has registered an identity");

                    if (blockchainIdentity.getCurrentUsername() == null) {
                        createPreorder(username, waitForFlag);
                    } else {
                        System.out.println("This wallet already has a username");
                    }
                } else {
                    System.out.println("This username $username already exists");
                }
            }
        }
    }

    private static void createIdentity(OptionSpec<WaitForEnum> waitForFlag, String username) {
        blockchainIdentity.registerIdentity(null, true);
        blockchainIdentity.watchIdentity(10, 2000, RetryDelayType.SLOW20, new RegisterIdentityCallback() {
            @Override
            public void onComplete(@NotNull String uniqueId) {
                System.out.println("Identity " + uniqueId + " has successfully been created");
                createPreorder(username, waitForFlag);
            }

            @Override
            public void onTimeout() {
                System.out.println("Failed to create identity.");
                waitAndShutdownFuture.set(waitForFlag);
            }
        });
    }

    private static void createPreorder(String username, final OptionSpec<WaitForEnum> waitForFlag) {
        blockchainIdentity.addUsername(username, false);
        List<String> names = blockchainIdentity.getUnregisteredUsernames();
        blockchainIdentity.registerPreorderedSaltedDomainHashesForUsernames(names, null);

        Map<String, byte[]> saltedDomainHashes = blockchainIdentity.saltedDomainHashesForUsernames(names);

        blockchainIdentity.watchPreorder(saltedDomainHashes, 10, 2000, RetryDelayType.SLOW20, new RegisterPreorderCallback() {
            @Override
            public void onTimeout(@NotNull List<String> incompleteNames) {
                System.out.println("These usernames were not preordered: " + incompleteNames);
                waitAndShutdownFuture.set(waitForFlag);
            }

            @Override
            public void onComplete(@NotNull List<String> names) {
                System.out.println("These usernames were preordered: " + names);
                try {
                    wallet.saveToFile(walletFile);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                createDomain(waitForFlag);
            }
        });
    }

    private static void createDomain(OptionSpec<WaitForEnum> waitForFlag) {
        List<String> names = blockchainIdentity.getUsernamesWithStatus(BlockchainIdentity.UsernameStatus.PREORDERED);
        blockchainIdentity.registerUsernameDomainsForUsernames(names, null);
        blockchainIdentity.watchUsernames(names, 10, 2000, RetryDelayType.SLOW20, new RegisterNameCallback() {
            @Override
            public void onTimeout(@NotNull List<String> incompleteNames) {
                System.out.println("These usernames were not registered: " + incompleteNames);
                waitAndShutdownFuture.set(waitForFlag);
            }

            @Override
            public void onComplete(@NotNull List<String> names) {
                System.out.println("These usernames were registered: " + names);
                try {
                    wallet.saveToFile(walletFile);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                sendContactRequests(waitForFlag);
            }
        });
    }

    static void sendContactRequests(OptionSpec<WaitForEnum> waitForFlag) {

        List<Document> names = platform.getNames().search("x-hash-eng-1-", Names.DEFAULT_PARENT_DOMAIN, false, 100, null);

        ContactRequests contacts = new ContactRequests(platform);
        for (Document name : names) {
            DomainDocument nameDoc = new DomainDocument(name);
            System.out.println("user: " + nameDoc.getLabel() + " " + nameDoc.getDashUniqueIdentityId());
            platform.getStateRepository().addValidIdentity(nameDoc.getDashUniqueIdentityId());
            Identity identity = platform.getIdentities().get(nameDoc.getDashUniqueIdentityId());
            contacts.create(blockchainIdentity, identity, null);
            try {
                sleep(1000);
            } catch (InterruptedException x) {

            }
        }

        List<Document> contactRequests = contacts.get(blockchainIdentity.getUniqueIdentifier(), false, 0, true, null);

        double completion = (double)contactRequests.size() / names.size();
        System.out.println("success rate on sending contact requests: " + completion);
        ArrayList<DomainDocument> missing = new ArrayList<>();
        for (Document name : names) {
            DomainDocument nameDoc = new DomainDocument(name);
            boolean found = false;
            for (Document req : contactRequests) {
                if (new ContactRequest(req).getToUserId().equals(nameDoc.getDashUniqueIdentityId()))
                    found = true;
            }

            if (!found)
                missing.add(nameDoc);
        }
        System.out.println("These contact requests had failures: " + missing);

        System.out.println(platform.client.reportNetworkStatus());

        waitAndShutdownFuture.set(waitForFlag);
    }

    private static void setCreationTime() {
        long creationTime = getCreationTimeSeconds();
        for (DeterministicKeyChain chain : wallet.getActiveKeyChains()) {
            DeterministicSeed seed = chain.getSeed();
            if (seed == null)
                System.out.println("Active chain does not have a seed: " + chain);
            else
                seed.setCreationTimeSeconds(creationTime);
        }
        if (creationTime > 0)
            System.out.println("Setting creation time to: " + Utils.dateTimeFormat(creationTime * 1000));
        else
            System.out.println("Clearing creation time.");
    }

    static synchronized void onChange(final CountDownLatch latch) {
        saveWallet(walletFile);
        Coin balance = wallet.getBalance(BalanceType.ESTIMATED);
        if (condition.matchBitcoins(balance)) {
            System.out.println(balance.toFriendlyString());
            latch.countDown();
        }
    }

    private static class WalletEventListener implements WalletChangeEventListener, WalletCoinsReceivedEventListener,
            WalletCoinsSentEventListener, WalletReorganizeEventListener {
        private final CountDownLatch latch;

        private  WalletEventListener(final CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onWalletChanged(Wallet wallet) {
            onChange(latch);
        }

        @Override
        public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            onChange(latch);
        }

        @Override
        public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            onChange(latch);
        }

        @Override
        public void onReorganize(Wallet wallet) {
            onChange(latch);
        }
    }
    private static final SettableFuture<OptionSpec<WaitForEnum>> waitAndShutdownFuture = SettableFuture.create();
    private static final FutureCallback<OptionSpec<WaitForEnum>> waitAndShutdownCallback = new FutureCallback<OptionSpec<WaitForEnum>>() {

        @Override
        public void onSuccess(@org.jetbrains.annotations.Nullable OptionSpec<WaitForEnum> result) {
            try {
                Context.propagate(context);
                waitAndShutdown(result);
            } catch (BlockStoreException x) {
                throw new RuntimeException(x);
            }
        }

        @Override
        public void onFailure(Throwable t) {

        }
    };

    static void status() {
        HashMap<DAPIAddress, org.dashj.platform.dapiclient.model.GetStatusResponse> results = new HashMap();

        ArrayList<String> nodeList = new ArrayList<>();

        context.masternodeListManager.getListAtChainTip().forEachMN(true, new SimplifiedMasternodeList.ForeachMNCallback() {
            @Override
            public void processMN(SimplifiedMasternodeListEntry mn) {
                nodeList.add(mn.getService().toSocketAddress().getHostString());
            }
        });
        int total = nodeList.size();
        int success = 0;
        int successFallback = 0;
        for (String node :nodeList) {
            Stopwatch watch = Stopwatch.createStarted();
            try {
                GetStatusResponse status = platform.client.getStatus(new DAPIAddress(node), 0);
                success++;
                results.put(status.getAddress(), status);
                System.out.println(node + ": Get status successful: "+ watch);
            } catch (Exception e) {
                watch.stop();
                System.out.println(node +" : Get status failed: " + e.getMessage() + " after "+ watch);
                results.put(new DAPIAddress(node), null);
            }
        }
        System.out.println("getStatus() Results: " + success + "/" + total + "(" + ((double)success)/total + ")");

        for (Map.Entry<DAPIAddress, GetStatusResponse> s : results.entrySet()) {
            System.out.println(s.getKey().getHost() +": " + (s.getValue() != null? s.getValue().getDuration() : "invalid") + " ms");
        }
    }

}
