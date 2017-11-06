import java.io.IOException;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Rafael Lima, rafaelslima@unifei.edu.br
 */


public class TokenServer {

    private static final int servPort = 8069;
    private static final int MSGMAX = 255;
    public List<TokenClient> clientesObjs;
    TokenServerView view;
    private Map<String, String> clientes = new HashMap<String, String>();
    private DatagramSocket socket = new DatagramSocket(servPort);
    private List<Integer> tokenRing = new ArrayList<Integer>();
    private boolean ringWorking = false;
    private String campoTexto;

    public TokenServer() throws IOException {

        this.clientesObjs = new ArrayList<TokenClient>();

        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(TokenServer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            Logger.getLogger(TokenServer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(TokenServer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            Logger.getLogger(TokenServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        //</editor-fold>
        System.out.print("\n[Servidor] Iniciado.\n");

        this.view = new TokenServerView(this);
        view.start();

        DatagramPacket inPacket = new DatagramPacket(new byte[MSGMAX], MSGMAX);

        check_presence();

        new Thread() {
            public void run() {
                /* block of code which need to execute via thread */
                try {
                    for (; ; ) { // Run forever, accpeting and echoing datagrams
                        socket.receive(inPacket); // Receive packet from client

                        analisarEntrada(inPacket);

                        inPacket.setLength(MSGMAX); // Reset length to avoid shrinking buffer
                    }
                } catch (IOException err) {
                    System.out.print(err);
                }
            }
        }.start();

    }

    public static void main(String[] args) throws IOException {

        TokenServer server = new TokenServer();

    }

    public static String cleanEntrada(DatagramPacket packet) {
        String c = new String(packet.getData(), StandardCharsets.UTF_8);

        String saida = c.substring(0, packet.getLength());

        return saida;
    }

    public void analisarEntrada(DatagramPacket packet) throws IOException {
        String entrada = cleanEntrada(packet);

        String[] dados = entrada.split("\\|");
        String tipo = dados[0];
        entrada = dados[1];

        switch (tipo) {
            case "auth":
                identificarCliente(packet, entrada);
                break;
            case "presence":
                presenceCliente(entrada);
                break;
            case "campoTexto":
                setCampoTexto(dados);
                break;
            default:
                System.out.print("[Servidor] Entrada não analisada");
        }

    }

    public void identificarCliente(DatagramPacket packet, String cliente_id) throws IOException {
        Map<String, String> user = new HashMap<String, String>();
        Map<String, String> cliente_hash = new HashMap<String, String>();

        user.put("ipCliente", packet.getAddress().getHostAddress());
        user.put("portCliente", "" + packet.getPort());
        user.put("isOnline", Boolean.TRUE.toString());
        user.put("isUpdated", Boolean.FALSE.toString());

        cliente_hash.put(cliente_id, user.toString());

        if (setCliente(cliente_hash)) {
            System.out.println("[Servidor] Novo cliente "
                    + packet.getAddress().getHostAddress() + " na porta "
                    + packet.getPort());
            // Envia a lista token ring registrada pelo servidor

        }

        socket.send(packet); // Envia id do cliente como ping

    }

    public void presenceCliente(String cliente_id) throws IOException {
        Map<String, String> user = stringToHash(clientes.get(cliente_id));
        user.put("isOnline", Boolean.TRUE.toString());
        clientes.put(cliente_id, user.toString());


        if (tokenRing.size() > 0) {
            enviar("tokenring", tokenRing.toString(), user);
        }

    }

    public int count_presence(){
        int atual = clientesObjs.size();
        int count = 0;
        for (TokenClient tc : clientesObjs){
            if(tc.isOnline()){
                count++;
            }

        }
        if(atual != count){
            System.out.println("Diferente");
        }
        return count;
    }
    public void check_presence() throws IOException {

        Timer timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                for (String key : clientes.keySet()) {
                    Map<String, String> user = null;
                    try {
                        user = stringToHash(clientes.get(key));
                        user.put("isOnline", Boolean.FALSE.toString());
                        clientes.put(key, user.toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

                for (String key : clientes.keySet()) {

                    try {

                        Map<String, String> user = stringToHash(clientes.get(key));
                        // Envia touch para o cliente

                        enviar("touch", key, user);
                        if(user.get("campoTexto") != null){
                            enviar("campoTexto", user.get("campoTexto"), user);
                        }

                    } catch (IOException e) {
                        System.err.print("[Servidor] Cliente erro");
                        System.err.print(e.toString());
                    }

                }
            }
        }, 2000, 2000);

        count_presence();
    }

    public boolean enviar(String tipo, String mensagem, Map<String, String> cliente) {

        try {
            String msg = new String(tipo + "|" + mensagem);
            byte[] bytesToSend = GetBytes(msg);

            InetAddress ipCliente = InetAddress.getByName(cliente.get("ipCliente"));
            int portCliente = Integer.parseInt(cliente.get("portCliente"));

            DatagramPacket sendPacket = new DatagramPacket(bytesToSend,
                    bytesToSend.length, ipCliente, portCliente);


            socket.send(sendPacket); // Envia touch para o cliente
            return true;
        } catch (IOException err) {
            return false;
        }
    }

    public List<Integer> getTokenRing() {
        return tokenRing;
    }

    public void setTokenRing(List<Integer> tokenRing) {
        this.tokenRing = tokenRing;
    }

    public void addToTokenRing(Integer clienteId) throws IOException {
        if (!this.tokenRing.contains(clienteId)) {
            this.tokenRing.add(clienteId);
            Map<String, String> cliente = stringToHash(clientes.get(clienteId.toString()));
            String clienteInfo = clienteId.toString() + "   -   IP: [" + cliente.get("ipCliente") +
                    "]  Porta: [" + cliente.get("portCliente") + "] Online: [" + cliente.get("isOnline") + "]";


            this.view.addClienteOnline(clienteInfo);

        }

    }

    public void removeFromTokenRing(Integer clienteId) {
        if (this.tokenRing.contains(clienteId)) {
            this.tokenRing.remove(clienteId);
            clientes.remove(clienteId.toString());
        }
    }

    public Integer getProxClienteId() {
        Integer proxId = getQtdClientes() + 1;
        List<Integer> tokenRing = getTokenRing();

        do {
            if (tokenRing.contains(proxId)) {
                proxId++;
            }
        } while (tokenRing.contains(proxId));

        return proxId;
    }

    public Integer getQtdClientes() {
        try {
            Integer qtd = clientes.size();
            return qtd;
        } catch (Exception err) {
            return 0;
        }
    }

    public Map<String, String> getClientes() {
        return clientes;
    }

    public boolean setCliente(Map<String, String> cliente_hash) {
        for (String key : cliente_hash.keySet()) {

            if (clientes.containsKey(key)) {
                System.out.println("[Servidor] Estou ouvindo, disse: " + key +
                        " = " + clientes.get(key));
                return false;
            } else {
                clientes.put(key, cliente_hash.get(key));
                try {
                    addToTokenRing(Integer.parseInt(key));
                } catch (IOException err) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    public Map<String, String> stringToHash(String str) throws IOException {
        Map<String, String> hash = new HashMap<String, String>();
        Properties props = new Properties();
        props.load(new StringReader(str.substring(1, str.length() - 1).replace(", ", "\n")));

        for (Map.Entry<Object, Object> e : props.entrySet()) {
            hash.put((String) e.getKey(), (String) e.getValue());
        }

        hash.put("campoTexto", getCampoTexto());
        return hash;
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

    public boolean isRingWorking() {
        return ringWorking;
    }

    public void setRingWorking(boolean ringWorking) {
        this.ringWorking = ringWorking;
    }


    public TokenClient criadorCliente() throws IOException {
        /*
        *           cliente(escuta, envia)
        *
        * cliente1(cliente3.envia, cliente2.escuta)
        * cliente2(cliente1.envia, cliente3.escuta)
        * cliente3(cliente2.envia, cliente1.escuta)
        *
        * */
        int listenPort = 0;
        int sendPort = 0;
        Integer proxId = getProxClienteId();
        Integer qtdClientes = getQtdClientes();

        if (!isRingWorking()) {
            setRingWorking(true);
        }

        if (qtdClientes == 0) {
            listenPort = 5000;
            sendPort = 5000;
        } else if (qtdClientes >= 1) {
            listenPort = alteraCliente();
            sendPort = 5000;
        }
        if (listenPort != 0 && sendPort != 0)
            return new TokenClient(proxId.toString(), listenPort, sendPort);
        else
            return null;
    }

    public int alteraCliente() throws IOException {
        int antId = tokenRing.size();
        TokenClient antObj = clientesObjs.get(antId - 1);
        int novoLinkPort = antObj.listenPort + 1;
        String changeInfo = "send|" + String.valueOf(novoLinkPort);
        Map<String, String> antCliente = stringToHash(clientes.get(String.valueOf(antId)));
        enviar("changeIO", changeInfo, antCliente);
        return novoLinkPort;
    }

    public String getCampoTexto() {
        return campoTexto;
    }

    public void setCampoTexto(String[] dados) throws IOException{
        String cliente_id = dados[1];
        for (String key : clientes.keySet()) {
            Map<String, String> user = stringToHash(clientes.get(key));
            if(cliente_id.equals(key)){
                user.put("isUpdated", Boolean.TRUE.toString());
            }
            else{
                user.put("isUpdated", Boolean.FALSE.toString());
            }
            clientes.put(cliente_id, user.toString());
        }
        String texto = dados[2];
        this.campoTexto = texto;
    }
}
