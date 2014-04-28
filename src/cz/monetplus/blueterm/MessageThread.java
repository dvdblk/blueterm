package cz.monetplus.blueterm;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

//import android.R.bool;
import android.content.Context;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;
import cz.monetplus.blueterm.bprotocol.BProtocol;
import cz.monetplus.blueterm.bprotocol.BProtocolFactory;
import cz.monetplus.blueterm.bprotocol.BProtocolMessages;
import cz.monetplus.blueterm.bprotocol.BProtocolTag;
import cz.monetplus.blueterm.frames.SLIPFrame;
import cz.monetplus.blueterm.frames.TerminalFrame;
import cz.monetplus.blueterm.server.ServerFrame;
import cz.monetplus.blueterm.util.MonetUtils;

/**
 * Thread for handling all messages.
 * 
 * @author "Dusan Krajcovic"
 * 
 */
public class MessageThread extends Thread {

    /**
     * String tag for logging.
     */
    private static final String TAG = "MessageThread";

    /**
     * Server connection ID. Only one serverconnection.
     */
    private byte[] serverConnectionID = null;

    /**
     * TCP client thread for read and write.
     */
    private static TCPClientThread tcpThread = null;

    /**
     * Message queue for handling messages from threads.
     */
    private Queue<Message> queue = new LinkedList<Message>();

    /**
     * Application context.
     */
    private Context applicationContext;

    /**
     * Terminal port (example 33333).
     */
    private int terminalPort;

    /**
     * Transaction input params.
     */
    private TransactionIn transactionInputData;

    /**
     * Transaction output params.
     */
    private TransactionOut transactionOutputData;

    /**
     * Stop this thread.
     */
    private boolean stopThread = false;

    /**
     * Member object for the chat services.
     */
    private TerminalServiceBT terminalService = null;

    /**
     * Terminal to muze posilat po castech.
     */
    private static ByteArrayOutputStream slipOutputpFraming = null;

    /**
     * @param context
     * @param terminalPort
     * @param transactionInputData
     */
    public MessageThread(final Context context, int terminalPort,
            TransactionIn transactionInputData) {
        super();

        slipOutputpFraming = new ByteArrayOutputStream();
        slipOutputpFraming.reset();

        applicationContext = context;
        this.terminalPort = terminalPort;
        this.transactionInputData = transactionInputData;
    }

    @Override
    public void run() {
        while (!stopThread) {
            if (queue.peek() != null) {
                handleMessage(queue.poll());
            }
        }

        if (tcpThread != null) {
            tcpThread.interrupt();
            tcpThread = null;
        }
    }

    /**
     * Get result from current thread.
     * 
     * @return TransactionOut result Data.
     */
    public TransactionOut getValue() {
        return transactionOutputData;
    }

    /**
     * Create and send pay request to terminal.
     */
    private void pay() {
        this.addMessage(HandleMessages.MESSAGE_TERM_WRITE, -1, -1, SLIPFrame
                .createFrame(new TerminalFrame(terminalPort, BProtocolMessages
                        .getSale(transactionInputData.getAmount(),
                                transactionInputData.getCurrency(),
                                transactionInputData.getInvoice()))
                        .createFrame()));
    }

    /**
     * Create and send handshake to terminal.
     */
    private void handshake() {
        this.addMessage(HandleMessages.MESSAGE_TERM_WRITE, -1, -1, SLIPFrame
                .createFrame(new TerminalFrame(terminalPort, BProtocolMessages
                        .getHanshake()).createFrame()));
    }

    /**
     * Create and send app info request to terminal.
     */
    private void appInfo() {
        this.addMessage(HandleMessages.MESSAGE_TERM_WRITE, -1, -1, SLIPFrame
                .createFrame(new TerminalFrame(terminalPort, BProtocolMessages
                        .getAppInfo()).createFrame()));
    }

    /**
     * @param what
     *            HandleMessages.
     * @param arg1
     *            TerminalState.
     * @param arg2
     *            TransactionCommand
     * @param obj
     *            Data for executing messages.
     */
    public void addMessage(int what, int arg1, int arg2, Object obj) {
        addMessage(Message.obtain(null, what, arg1, arg2, obj));
    }

    /**
     * @param what
     *            HandleMessages.
     * @param arg1
     *            TerminalState.
     * @param arg2
     *            TransactionCommand
     */
    public void addMessage(int what, int arg1, int arg2) {
        addMessage(Message.obtain(null, what, arg1, arg2));
    }

    /**
     * @param what
     *            HandleMessages.
     */
    public void addMessage(int what) {
        addMessage(Message.obtain(null, what));
    }

    /**
     * @param msg
     *            Message for addding to queue.
     */
    public void addMessage(Message msg) {
        queue.add(msg);
    }

    /**
     * @param service
     *            Terminal service serving bluetooth.
     */
    public void setTerminalService(TerminalServiceBT service) {
        this.terminalService = service;
    }

    // @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
        case HandleMessages.MESSAGE_STATE_CHANGE:
            handleStateChange(msg);
            break;
        // case HandleMessages.MESSAGE_SERVER_WRITE:
        // break;
        // case HandleMessages.MESSAGE_SERVER_READ:
        // break;

        case HandleMessages.MESSAGE_TERM_WRITE:
            // Jedine misto v aplikaci pres ktere se posila do terminalu
            write2Terminal((byte[]) msg.obj);
            break;

        case HandleMessages.MESSAGE_CONNECTED:
            // Send to terminal information about connection at server.
            connectionRequest(msg);
            break;

        case HandleMessages.MESSAGE_TERM_READ:
            handleTermReceived(msg);
            break;
        case HandleMessages.MESSAGE_DEVICE_NAME:
            // Nemam tuseni k cemu bych to vyuzil
            break;
        case HandleMessages.MESSAGE_TOAST:
            Toast.makeText(applicationContext, msg.obj.toString(),
                    Toast.LENGTH_SHORT).show();
            break;
        case HandleMessages.MESSAGE_QUIT:
            this.stopThread();
            break;
        }
    }

    /**
     * Sends a message.
     * 
     * @param message
     *            A string of text to send.
     */
    private void write2Terminal(byte[] message) {
        // Check that we're actually connected before trying anything
        if (terminalService.getState() != TerminalState.STATE_CONNECTED) {

            // Toast.makeText(applicationContext, R.string.not_connected,
            // Toast.LENGTH_SHORT).show();
            this.addMessage(HandleMessages.MESSAGE_TOAST, -1, -1,
                    R.string.not_connected);
            return;
        }

        // Check that there's actually something to send
        if (message.length > 0) {
            terminalService.write(message);
        }
    }

    /**
     * Send to terminal information about connection at server.
     * 
     * @param msg
     *            Contains status(arg1) about current connection to server.
     * */
    private void connectionRequest(Message msg) {
        byte[] status = new byte[1];
        status[0] = (byte) msg.arg1;
        ServerFrame soFrame = new ServerFrame(
                TerminalCommands.TERM_CMD_SERVER_CONNECTED, serverConnectionID,
                status);
        TerminalFrame toFrame = new TerminalFrame(
                TerminalPorts.SERVER.getPortNumber(), soFrame.createFrame());

        this.addMessage(HandleMessages.MESSAGE_TERM_WRITE, -1, -1,
                SLIPFrame.createFrame(toFrame.createFrame()));
    }

    /**
     * Received message from terminal
     * 
     * @param msg
     *            Messaget contains information read from terminal.
     */
    private void handleTermReceived(Message msg) {
        slipOutputpFraming.write((byte[]) msg.obj, 0, msg.arg1);

        // Check
        if (SLIPFrame.isFrame(slipOutputpFraming.toByteArray())) {

            TerminalFrame termFrame = new TerminalFrame(
                    SLIPFrame.parseFrame(slipOutputpFraming.toByteArray()));
            slipOutputpFraming.reset();

            if (termFrame != null) {
                switch (termFrame.getPort()) {
                case UNDEFINED:
                    Log.d(TAG, "undefined port");
                    break;
                case SERVER:
                    // messages for server
                    handleServerMessage(termFrame);
                    break;
                case FLEET:
                    Log.d(TAG, "fleet data");
                    break;
                case MAINTENANCE:
                    Log.d(TAG, "maintentace data");
                    break;
                case MASTER:
                    // Tyhle zpravy zpracovavat, jsou pro tuhle
                    // aplikaci
                    BProtocol bprotocol = new BProtocolFactory()
                            .deserialize(termFrame.getData());

                    if (bprotocol.getProtocolType().equals("B2")) {
                        transactionOutputData = new TransactionOut();
                        try {
                            transactionOutputData.setResultCode(Integer
                                    .valueOf(bprotocol.getTagMap().get(
                                            BProtocolTag.ResponseCode)));
                        } catch (Exception e) {
                            transactionOutputData.setResultCode(-1);
                        }
                        transactionOutputData.setMessage(bprotocol.getTagMap()
                                .get(BProtocolTag.ServerMessage));
                        try {
                            transactionOutputData.setAuthCode(Integer
                                    .valueOf(bprotocol.getTagMap().get(
                                            BProtocolTag.AuthCode)));
                        } catch (Exception e) {
                            transactionOutputData.setAuthCode(0);
                        }
                        try {
                            transactionOutputData.setSeqId(Integer
                                    .valueOf(bprotocol.getTagMap().get(
                                            BProtocolTag.SequenceId)));
                        } catch (Exception e) {
                            transactionOutputData.setSeqId(0);
                        }
                        transactionOutputData.setCardNumber(bprotocol
                                .getTagMap().get(BProtocolTag.PAN));
                        transactionOutputData.setCardType(bprotocol.getTagMap()
                                .get(BProtocolTag.CardType));

                        this.stopThread();
                    }

                    break;
                default:
                    // Nedelej nic, spatne data, format, nebo
                    // crc
                    Log.e(TAG, "Invalid port");
                    break;

                }
            }

        } else {
            Log.e(TAG, "Corrupted data. It's not slip frame.");
        }
    }

    private void stopThread() {
        terminalService.stop();
        stopThread = true;
    }

    private void handleStateChange(Message msg) {
        Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
        switch (msg.arg1) {
        case TerminalState.STATE_CONNECTED:
            if (msg.arg2 >= 0) {
                switch (TransactionCommand.values()[msg.arg2]) {
                case HANDSHAKE:
                    handshake();
                    break;
                case INFO:
                    appInfo();
                    break;
                case PAY:
                    pay();
                    break;
                case UNKNOWN:
                    break;
                default:
                    break;

                }
            }
            break;
        case TerminalState.STATE_CONNECTING:
        case TerminalState.STATE_LISTEN:
            break;
        case TerminalState.STATE_NONE:
            break;
        }
    }

    private void handleServerMessage(TerminalFrame termFrame) {
        // sends the message to the server
        final ServerFrame serverFrame = new ServerFrame(termFrame.getData());

        Log.d(TAG, "Server command: " + serverFrame.getCommand());
        switch (serverFrame.getCommand()) {
        case TerminalCommands.TERM_CMD_ECHO:
            echoResponse(termFrame, serverFrame);
            break;

        case TerminalCommands.TERM_CMD_CONNECT:
            serverConnectionID = serverFrame.getId();

            int port = MonetUtils.getInt(serverFrame.getData()[4],
                    serverFrame.getData()[5]);

            int timeout = MonetUtils.getInt(serverFrame.getData()[6],
                    serverFrame.getData()[7]);

            // connect to the server
            tcpThread = new TCPClientThread(this);
            tcpThread.setConnection(
                    Arrays.copyOfRange(serverFrame.getData(), 0, 4), port,
                    timeout, serverFrame.getIdInt());
            Log.i(TAG, "TCP thread starting.");
            tcpThread.start();

            TerminalFrame responseTerminal = new TerminalFrame(termFrame
                    .getPort().getPortNumber(), new ServerFrame(
                    (byte) TerminalCommands.TERM_CMD_CONNECT_RES,
                    serverFrame.getId(), new byte[1]).createFrame());

            this.addMessage(HandleMessages.MESSAGE_TERM_WRITE, -1, -1,
                    SLIPFrame.createFrame(responseTerminal.createFrame()));

            break;

        case TerminalCommands.TERM_CMD_DISCONNECT:
            if (tcpThread != null) {
                tcpThread.interrupt();
                tcpThread = null;
            }
            break;

        case TerminalCommands.TERM_CMD_SERVER_WRITE:
            // Send data to server.
            tcpThread.sendMessage(serverFrame.getData());
        }

    }

    /**
     * Terminal check this application.
     * 
     * @param termFrame
     *            Terminal frame.
     * @param serverFrame
     *            Server frame.
     */
    private void echoResponse(TerminalFrame termFrame,
            final ServerFrame serverFrame) {
        TerminalFrame responseTerminal = new TerminalFrame(termFrame.getPort()
                .getPortNumber(),
                new ServerFrame(TerminalCommands.TERM_CMD_ECHO_RES, serverFrame
                        .getId(), null).createFrame());

        this.addMessage(HandleMessages.MESSAGE_TERM_WRITE, -1, -1,
                SLIPFrame.createFrame(responseTerminal.createFrame()));
    }
}