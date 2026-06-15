package client;

import java.awt.*;
import javax.swing.*;

public class ChatFrame extends JFrame {

    public JTextArea chatArea;
    public JTextField txtMessage;
    public JButton btnSend;

    public ChatFrame() {

        setTitle("Chat");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        chatArea = new JTextArea();
        chatArea.setEditable(false);

        txtMessage = new JTextField();

        btnSend = new JButton("Send");

        JPanel panel = new JPanel(new BorderLayout());

        panel.add(txtMessage, BorderLayout.CENTER);
        panel.add(btnSend, BorderLayout.EAST);

        add(new JScrollPane(chatArea), BorderLayout.CENTER);
        add(panel, BorderLayout.SOUTH);

        setVisible(true);
    }
}
