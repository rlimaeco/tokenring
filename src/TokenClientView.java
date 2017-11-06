import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
/*
 *
 * @author Rafael Lima, rafaelslima@unifei.edu.br
 */
public class TokenClientView{


    TokenClient tokenClient;
    JFrame frame;
    private JPanel panelMain;
    private JLabel labelTitle;
    private JButton btnLiberar;
    private JButton btnPedirAcesso;
    private JTextArea campoTexto;
    private JLabel labelEditMode;


    public TokenClientView(TokenClient cliente) {
        tokenClient = cliente;
        frame = new JFrame("Cliente " + tokenClient.getClienteId());
        labelTitle.setText("Cliente " + cliente.getClienteId());

        btnPedirAcesso.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cliente.requestToken = true;
            }
        });

        btnLiberar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    // TODO add your handling code here:
                    labelEditMode.setVisible(false);
                    cliente.requestToken = false;
                    campoTexto.setEditable(false);
                    System.out.println("Acesso Liberado");
                    Socket clntSock2 = new Socket("localhost", tokenClient.sendPort);
                    PrintStream ps = new PrintStream(clntSock2.getOutputStream());
                    ps.println("token");
                    clntSock2.close();
                } catch (IOException ex) {
                    Logger.getLogger(TokenClientView.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }




    public void start() {

        frame.setContentPane(this.panelMain);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

    }

    public JTextArea getCampoTexto() {
        return campoTexto;
    }

    public void setCampoTextoEditable(boolean valor) {
        this.campoTexto.setEditable(valor);
    }

    public void setLabelEditMode(boolean visible) {
        this.labelEditMode.setVisible(visible);
    }
}
