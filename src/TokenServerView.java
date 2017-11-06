import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TokenServerView {
    private JList clientesOnline;
    private JButton adicionarButton;
    private JButton removerButton;
    private JPanel panelMain;
    private JPanel panelClient;
    private JScrollPane clientesScroll;
    private JLabel labelClientesAtivos;
    private JLabel labelTitle;
    private JPanel panelButtons;

    DefaultListModel<String> clientesModel = new DefaultListModel<>();

    public TokenServerView(TokenServer servidor) {


        this.clientesOnline.setModel(this.clientesModel);


        adicionarButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                /*
                * cliente(escuta, envia)
                *
                * cliente1(cliente3.envia, cliente2.escuta)
                * cliente2(cliente1.envia, cliente3.escuta)
                * cliente3(cliente2.envia, cliente1.escuta)
                *
                * */
                if (servidor != null) {
                    new Thread() {
                        public void run() {
                            try {
                                TokenClient cli = servidor.criadorCliente();
                                if (cli != null)
                                    servidor.clientesObjs.add(cli);
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                        }
                    }.start();
                }

            }
        });
        removerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (clientesModel.getSize() > 0) {
                    int index = clientesOnline.getSelectedIndex();
                    List<Integer> tokenRing = servidor.getTokenRing();
                    String clienteId = tokenRing.get(index).toString();
                    clientesModel.removeElementAt(index);
                    int indexToDel = 0;

                    for (TokenClient tc : servidor.clientesObjs) {
                        if (clienteId.equals(tc.getClienteId())) {
                            System.out.println("[Servidor] Cliente " + clienteId + " removido");

                            indexToDel = servidor.clientesObjs.indexOf(tc);

                        }
                    }
                    TokenClient cli = servidor.clientesObjs.get(indexToDel);
                    cli.view.frame.setVisible(false);
                    cli.view.frame.dispose();
                    cli = null;
                    servidor.clientesObjs.remove(indexToDel);

                    servidor.removeFromTokenRing(Integer.parseInt(clienteId));
                }
            }
        });
    }


    public JList getClientesOnline() {
        return clientesOnline;
    }

    public void setClientesOnline(JList clientesOnline) {
        this.clientesOnline = clientesOnline;


    }

    public void addClienteOnline(String cliente) {
        this.clientesModel.addElement(cliente);
    }

    public void start() {
        JFrame frame = new JFrame("TokenServerView");
        frame.setContentPane(this.panelMain);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

}
