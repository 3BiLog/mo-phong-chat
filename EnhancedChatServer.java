package mo_phong_zalo2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class EnhancedChatServer extends JFrame {
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private JTextArea logArea;
    private JButton startBtn, stopBtn;

    public EnhancedChatServer() {
        setTitle("🔌 Zalo Server");
        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        startBtn = new JButton("Start Server");
        stopBtn = new JButton("Stop Server");
        stopBtn.setEnabled(false);

        controlPanel.add(startBtn);
        controlPanel.add(stopBtn);
        add(controlPanel, BorderLayout.SOUTH);

        startBtn.addActionListener(e -> startServer());
        stopBtn.addActionListener(e -> stopServer());

        setVisible(true);
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(8888);
            pool = Executors.newCachedThreadPool();
            log("✅ Server started on port 8888");
            startBtn.setEnabled(false);
            stopBtn.setEnabled(true);

            pool.execute(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket client = serverSocket.accept();
                        ClientHandler handler = new ClientHandler(client);
                        clients.add(handler);
                        pool.execute(handler);
                        log("🟢 Client connected: " + client);
                    } catch (IOException e) {
                        log("❌ Server stopped.");
                        break;
                    }
                }
            });
        } catch (IOException ex) {
            log("❌ Error starting server: " + ex.getMessage());
        }
    }

    private void stopServer() {
        try {
            for (ClientHandler ch : clients) ch.close();
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (pool != null && !pool.isShutdown()) {
                pool.shutdownNow();
            }
            log("🛑 Server stopped.");
            startBtn.setEnabled(true);
            stopBtn.setEnabled(false);
        } catch (IOException ex) {
            log("⚠ Error stopping server: " + ex.getMessage());
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append(msg + "\n"));
    }

    class ClientHandler implements Runnable {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private final String username;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            this.username = in.readUTF();
            broadcast("TEXT", AESUtil.encrypt(username + " has joined the chat."));
        }

        public void run() {
            try {
                while (true) {
                    String type = in.readUTF();
                    switch (type) {
                        case "TEXT":
                            String encryptedMsg = in.readUTF();
                            broadcast("TEXT", encryptedMsg);
                            break;
                        case "FILE":
                        case "IMAGE":
                            receiveAndBroadcastFile(type);
                            break;

                        case "VANISH":
                            String msg = in.readUTF();
                            int seconds = in.readInt();
                            broadcastVanish(msg, seconds);
                            break;
                        case "AUDIO":
                            receiveAndBroadcastFile(type);
                            break;
                    }
                }
            } catch (IOException e) {
                log("🔴 Disconnected: " + username);
            } finally {
                clients.remove(this);
                broadcast("TEXT", AESUtil.encrypt(username + " has left the chat."));
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

        private void receiveAndBroadcastFile(String type) throws IOException {
            String fileName = in.readUTF();
            String sender = in.readUTF();
            long size = in.readLong();
            byte[] buffer = new byte[(int) size];
            in.readFully(buffer);

            for (ClientHandler client : clients) {
                if (client != this) {
                    client.sendFile(type, fileName, sender, buffer);
                }
            }
        }

        private void sendFile(String type, String fileName, String sender, byte[] data) throws IOException {
            out.writeUTF(type);
            out.writeUTF(fileName);
            out.writeUTF(sender);
            out.writeLong(data.length);
            out.write(data);
        }

        public void close() throws IOException {
            socket.close();
        }

        private void broadcast(String type, String data) {
            for (ClientHandler client : clients) {
                try {
                    client.out.writeUTF(type);
                    client.out.writeUTF(data);
                } catch (IOException e) {
                    log("Error sending to " + client.username);
                }
            }
        }
    }

    private void broadcastVanish(String encryptedMsg, int seconds) {
        for (ClientHandler client : clients) {
            try {
                client.out.writeUTF("VANISH");
                client.out.writeUTF(encryptedMsg);
                client.out.writeInt(seconds);
            } catch (IOException e) {
                log("Lỗi gửi VANISH tới " + client.username);
            }
        }
    }

    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(EnhancedChatServer::new);
    }
}
