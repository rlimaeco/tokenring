import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Rafael Lima, rafaelslima@unifei.edu.br
 */

public class TokenClient {

    private static final int TIMEOUT = 3000; // Tempo limite de tentativas de envio (millisegundos)
    private static final int MAXTRIES = 5; // Número máximo de tentativas de reconexão
    private static final int MSGMAX = 255;
    private String clienteId;
    boolean requestToken = false;
    DatagramSocket socket = new DatagramSocket();
    InetAddress serverAddress = InetAddress.getByName("127.0.0.1");
    byte[] bytesToSend = new byte[MSGMAX];
    int servPort = 8069;
    TokenClientView view;
    Socket clntSock = null;
    // Atributos Para TokenRing
    int listenPort;
    int sendPort;
    private boolean online = false;
    private boolean ringWorking = false;
    private boolean tokenStarted = false;
    private Thread listenThread;
    private List<Integer> tokenRing = new ArrayList<Integer>();
    private volatile boolean running = false;


    public TokenClient(String clienteId, int listenPort, int sendPort) throws IOException {
        if (listenPort == 0 || sendPort == 0) {
            if (running) {
                running = false;
            }
            throw new IOException("Deve haver porta de entrada e saida");
        } else {
            running = true;
        }
        System.out.print("\n[Cliente "+ clienteId+"] Iniciando conexão com servidor...\n");
        setClienteId(clienteId);
        this.listenPort = listenPort;
        this.sendPort = sendPort;

        this.listenThread = listenTokenThread();
        this.listenThread.start();

        this.view = new TokenClientView(this);
        view.start();

        socket.setSoTimeout(TIMEOUT); // Tempo máximo de bloqueio de recepção (millisegundos)

        String auth = new String("auth|" + clienteId);

        byte[] bytesToSend = GetBytes(auth);

        DatagramPacket sendPacket = new DatagramPacket(bytesToSend,
                bytesToSend.length, serverAddress, servPort);

        DatagramPacket receivePacket = new DatagramPacket(new byte[bytesToSend.length],
                bytesToSend.length);

        int tries = 0;
        boolean receivedResponse = false;

        do {
            socket.send(sendPacket); // Envia identificação ao servidor
            try {
                socket.receive(receivePacket); // Tenta receber a resposta

                if (!receivePacket.getAddress().equals(serverAddress)) // Verifica origem
                    throw new IOException("Pacote de fonte desconhecida");

                receivedResponse = true;
            } catch (InterruptedIOException e) { // We didn't get anything
                tries += 1;
                System.out.println("[Cliente "+ clienteId+"] Tempo limite, " + (MAXTRIES - tries) +
                        " tentando novamente ...");
            }
        } while ((!receivedResponse) && (tries < MAXTRIES));

        if (receivedResponse) {
            System.out.println("[Cliente] Dispositivo: " + new String(receivePacket.getData()) + " conectado");
            setOnline(true);
//            check_presence();
            socketListenForever();


        } else {
            System.out.println("[Cliente "+ clienteId+"] Sem resposta -- desconectando.");
            if (isOnline()) setOnline(false);
        }

    }

    private static String cleanEntrada(DatagramPacket packet) {
        String c = new String(packet.getData(), StandardCharsets.UTF_8);

        String saida = c.substring(0, packet.getLength());

        return saida;
    }

    public void terminate() {
        running = false;
    }

    private void analisarEntrada(DatagramPacket packet) throws IOException {
        String entrada = cleanEntrada(packet);

        String[] dados = entrada.split("\\|");
        String tipo = dados[0];

        if (dados.length > 1) {
            entrada = dados[1];
        }

        switch (tipo) {
            case "auth":
//                identificarCliente(packet, entrada);
//                System.out.print("Entrada auth");
                break;
            case "touch":
                touchPresence();
                break;
            case "tokenring":
                tokenRingUpdate(entrada);
                break;
            case "changeIO":
                changeIO(dados);
                break;
            default:
                System.out.print("\n[Cliente "+ clienteId+"] Entrada não analisada\n");
                System.out.print(entrada);
        }

    }

    public void startToken() {
        try {
            esperar(500);
            System.out.println("\n[Cliente "+ clienteId+"] Iniciando Token");
            Socket clntSock2 = new Socket("localhost", sendPort);
            PrintStream ps = new PrintStream(clntSock2.getOutputStream());
            ps.println("token");
            clntSock2.close();
            setTokenStarted(true);
        } catch (IOException ex) {
            Logger.getLogger(TokenClientView.class.getName()).log(Level.SEVERE, null, ex);

        }

    }

    public void changeIO(String[] dados){
        boolean isModificado = false;

        if (dados[1].equals("send")) {
            sendPort = Integer.parseInt(dados[2]);
            isModificado = true;
        } else if (dados[1].equals("listen")) {
            listenPort = Integer.parseInt(dados[2]);
            isModificado = true;
        } else if (dados[1].equals("reset")) {
            isModificado = true;
        }

        if (isModificado) {
//            try {
//                clntSock.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            terminate();
//            listenThread = null;
//            running = true;
//            listenThread = listenTokenThread();
//            listenThread.start();

            esperar(2000);

            startToken();


        }
    }

    public void listenToken() {

        Scanner in;
        PrintStream ps;


        try {

            ServerSocket servSock = new ServerSocket(listenPort);

            for (;;) {

                clntSock = servSock.accept();

                in = new Scanner(clntSock.getInputStream());

                if (in.nextLine().compareTo("token") == 0) {
                    if (requestToken) {
                        System.out.println("[Cliente "+ clienteId+"] Acesso Requisitado");
                        this.view.setCampoTextoEditable(true);
                        this.view.setLabelEditMode(true);
                    } else {
                        try {
                            System.out.println("[Cliente "+ clienteId+"] Acesso não Requisitado");
                            Thread.sleep(2000);
                            Socket clntSock2 = new Socket("localhost", sendPort);
                            ps = new PrintStream(clntSock2.getOutputStream());
                            ps.println("token");
                            ps.close();
                            clntSock2.close();
                        } catch (InterruptedException ex) {
                            running = false;
                            clntSock.close();
                            break;
                        }
                    }
                }

                clntSock.close();


            }

        } catch (IOException ex) {
//            clntSock.close();
//            clntSock = null;
//            clntSock.isClosed();
//            ex.printStackTrace();
            listenThread = null;
            Thread.currentThread().interrupt();

        }


    }

    public Thread listenTokenThread() {
        return new Thread() {
            public void run() {
                synchronized (this) {
                    listenToken();
                }
            }
        };
    }

    public void socketListenForever() throws IOException {

        DatagramPacket receivePacket = new DatagramPacket(new byte[bytesToSend.length],
                bytesToSend.length);


        Timer timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                boolean receivedResponse = false;

                try {
                    do {

                        try {
                            socket.receive(receivePacket); // Tenta receber a resposta

                            if (!receivePacket.getAddress().equals(serverAddress)) // Verifica origem
                                throw new IOException("Pacote de fonte desconhecida");

                            receivedResponse = true;
                        } catch (InterruptedIOException e) { // We didn't get anything
                            continue;
                        }
                    } while (!receivedResponse);

                    if (receivedResponse) {
                        analisarEntrada(receivePacket);
                    }
                } catch (IOException erro) {
                    System.err.print("[Cliente "+ clienteId+"] Perdeu comunicação com servidor");
                    setOnline(false);
                    socket.close();
                }
            }
        }, 2500, 2500);
    }

    public void touchPresence() throws IOException {

        String touch = new String("presence|" + clienteId);

        byte[] bytesToSend = GetBytes(touch);

        DatagramPacket sendPacket = new DatagramPacket(bytesToSend,
                bytesToSend.length, serverAddress, servPort);

        // Diminuir log
//        System.out.print("[Cliente] Presença id [" + clienteId + "] online\n");
        socket.send(sendPacket);

    }

    public boolean enviar(String tipo, String mensagem, Map<String, String> cliente) {

        try {
            String msg = new String(tipo + "|" + mensagem);
            byte[] bytesToSend = GetBytes(msg);

            DatagramPacket sendPacket = new DatagramPacket(bytesToSend,
                    bytesToSend.length, serverAddress, servPort);


            socket.send(sendPacket); // Envia mensagem para o servidor
            return true;
        } catch (IOException err) {
            return false;
        }
    }


//    public static void main(String[] args) throws IOException {
//
//        TokenClient cliente = new TokenClient("1");
//
//    }

    public void tokenRingUpdate(String entrada) {

        entrada = entrada.replace("[", "");
        entrada = entrada.replace("]", "");

        if (entrada.length() > 1) {
            String[] dados = entrada.split(",");
            List<Integer> tkring = new ArrayList<>();

            for (String clienteId : dados) {
                tkring.add(Integer.parseInt(clienteId.replaceAll("\\s+", "")));

            }

            setTokenRing(tkring);
        } else if (entrada.length() == 1) {
            List<Integer> tkring = new ArrayList<>();
            tkring.add(Integer.parseInt(entrada.replaceAll("\\s+", "")));
            setTokenRing(tkring);
        }


        if (tokenRing.size() >= 1) {
            setRingWorking(true);
            running = true;


            if (!isTokenStarted()) {
                startToken();
            }
        }


    }

    public List<Integer> getTokenRing() {
        return tokenRing;
    }

    public void setTokenRing(List<Integer> tokenRing) {
        this.tokenRing = tokenRing;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public byte[] GetBytes(String str) {
        char[] chars = str.toCharArray();
        byte[] bytes = new byte[chars.length];
        for (int i = 0; i < chars.length; i++) {

            bytes[i] = (byte) chars[i];
        }
        return bytes;
    }

    public String GetString(byte[] bytes) {
        char[] chars = new char[bytes.length];

        for (int i = 0; i < chars.length; i++)
            chars[i] = (char) (bytes[i] & 0xFF);

        return new String(chars);

    }

    public Map<String, String> stringToHash(String str) throws IOException {
        Map<String, String> hash = new HashMap<String, String>();
        Properties props = new Properties();
        props.load(new StringReader(str.substring(1, str.length() - 1).replace(", ", "\n")));

        for (Map.Entry<Object, Object> e : props.entrySet()) {
            hash.put((String) e.getKey(), (String) e.getValue());
        }

        return hash;
    }

    public void esperar(int tempoMiliseg){
        try {
            Thread.sleep(tempoMiliseg);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean isRingWorking() {
        return ringWorking;
    }

    public void setRingWorking(boolean ringWorking) {
        this.ringWorking = ringWorking;
    }

    public boolean isTokenStarted() {
        return tokenStarted;
    }

    public void setTokenStarted(boolean tokenStarted) {
        this.tokenStarted = tokenStarted;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }
}
