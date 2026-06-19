package UI;

import java.awt.*;
import java.io.PrintWriter;
import javax.swing.*;

public class ChatFrame extends JFrame {

    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private PrintWriter writer;

    public ChatFrame(PrintWriter writer) {

        this.writer = writer;

        setTitle("Chat Application");
        setSize(700, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);

        JScrollPane scrollPane =
                new JScrollPane(chatArea);

        add(scrollPane, BorderLayout.CENTER);

        JPanel inputPanel =
                new JPanel(new BorderLayout());

        messageField = new JTextField();
        sendButton = new JButton("Send");

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        add(inputPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(
                e -> sendMessage());

        messageField.addActionListener(
                e -> sendMessage());
    }

    private void sendMessage() {

        String message =
                messageField.getText().trim();

        if(message.isEmpty()) {
            return;
        }

        writer.println(message);

        messageField.setText("");
    }

    public void appendMessage(String message) {
        chatArea.append(message + "\n");
    }
}