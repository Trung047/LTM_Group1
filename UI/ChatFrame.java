package UI;

import Client.MessageReceiver;

import javax.swing.*;
import java.io.*;
import java.net.Socket;

public class ChatFrame extends JFrame {

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private ChatPanel chatPanel;

    public ChatFrame(String username, String host, int port) {
        setTitle("NetChat - " + username);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        chatPanel = new ChatPanel();
        chatPanel.setMyUsername(username);
        chatPanel.setConnInfo(host + ":" + port);

        add(chatPanel);

        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            chatPanel.appendSystemMessage("Đã kết nối server.");

            chatPanel.setChatListener(new ChatPanel.ChatListener() {
                @Override
                public void onSendMessage(String text) {
                    writer.println("[" + username + "] : " + text);
                }

                @Override
                public void onDisconnect() {
                    closeConnection();
                }
            });

            new MessageReceiver(reader, chatPanel, username).start();

        } catch (IOException e) {
            chatPanel.appendSystemMessage("Không kết nối được server: " + e.getMessage());
        }
    }

    private void closeConnection() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        dispose();
    }
}
