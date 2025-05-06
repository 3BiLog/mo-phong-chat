package mo_phong_zalo2;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Base64;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import javax.sound.sampled.*;

public class EnhancedChatClient extends JFrame {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private JTextArea chatArea;
    private JTextField inputField;
    private DefaultListModel<String> emojiList;
    private JButton sendBtn, fileBtn, imgBtn, emojiBtn, stegoBtn, vanishBtn, revealBtn, audioBtn;
    private JFileChooser fileChooser;
    private JPanel mediaPanel;
    private String username;

    public EnhancedChatClient(String username) {
        this.username = username;
        setTitle("Zalo Client - " + username);
        setSize(600, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        inputField = new JTextField();
        sendBtn = new JButton("Gửi");
        fileBtn = new JButton("📁");
        vanishBtn = new JButton("🕒");
        imgBtn = new JButton("🖼");
        emojiBtn = new JButton("😀");
        stegoBtn = new JButton("🕵️");
        revealBtn = new JButton("🔍");
        audioBtn = new JButton("🎤");

        JPanel inputPanel = new JPanel(new BorderLayout());
        JPanel btnPanel = new JPanel(new FlowLayout());

        btnPanel.add(sendBtn);
        btnPanel.add(fileBtn);
        btnPanel.add(vanishBtn);
        btnPanel.add(imgBtn);
        btnPanel.add(emojiBtn);
        btnPanel.add(stegoBtn);
        btnPanel.add(revealBtn);
        btnPanel.add(audioBtn);

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(btnPanel, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        mediaPanel = new JPanel();
        mediaPanel.setLayout(new BoxLayout(mediaPanel, BoxLayout.Y_AXIS));
        add(mediaPanel, BorderLayout.EAST);

        fileChooser = new JFileChooser();

        setupSocket();
        setupListeners();
        receiveMessages();

        setVisible(true);
    }

    private void setupSocket() {
        try {
            socket = new Socket("127.0.0.1", 8888);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(username);
        } catch (IOException e) {
            showError("Không thể kết nối server: " + e.getMessage());
        }
    }

    private void setupListeners() {
        sendBtn.addActionListener(e -> sendText());
        fileBtn.addActionListener(e -> sendFile());
        vanishBtn.addActionListener(e -> sendSelfDestructMessage());
        imgBtn.addActionListener(e -> sendImage());
        emojiBtn.addActionListener(e -> showEmojiPopup());
        stegoBtn.addActionListener(e -> sendStegoImage());
        revealBtn.addActionListener(e -> revealStegoImage());
        audioBtn.addActionListener(e -> sendAudio());
    }

    private void sendText() {
        try {
            String text = inputField.getText();
            if (text.isEmpty()) return;
            String encrypted = AESUtil.encrypt(username + ": " + text);
            out.writeUTF("TEXT");
            out.writeUTF(encrypted);
            System.out.println("[DEBUG] Encrypted text: " + encrypted);
            inputField.setText("");
            
        } catch (Exception e) {
            showError("Lỗi gửi tin nhắn");
        }
    }

    private void sendFile() {
        fileChooser.setDialogTitle("Chọn file");
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
            	byte[] raw = Files.readAllBytes(file.toPath());
            	byte[] encrypted = AESUtil.encryptBytes(raw);
            	out.writeUTF("FILE");
            	out.writeUTF(file.getName());
            	out.writeUTF(username);
            	out.writeLong(encrypted.length);
            	out.write(encrypted);
            	chatArea.append("Bạn đã gửi FILE: " + file.getName() + "\n");
            } catch (IOException e) {
                showError("Lỗi gửi file");
            }
        }
    }

    private void sendSelfDestructMessage() {
        try {
            String text = inputField.getText();
            if (text.isEmpty()) return;

            String input = JOptionPane.showInputDialog(this, "Tin nhắn sẽ tự hủy sau bao nhiêu giây?", "5");
            if (input == null || input.isEmpty()) return;

            int seconds = Integer.parseInt(input);
            String encrypted = AESUtil.encrypt(username + ": " + text);

            out.writeUTF("VANISH");
            out.writeUTF(encrypted);
            out.writeInt(seconds); // gửi thời gian tự hủy

            inputField.setText("");
        } catch (Exception e) {
            showError("Lỗi gửi tin tự hủy");
        }
    }

    
    private void sendImage() {
        fileChooser.setDialogTitle("Chọn ảnh");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Hình ảnh", "jpg", "png", "jpeg"));
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
            	byte[] raw = Files.readAllBytes(file.toPath());
            	byte[] encrypted = AESUtil.encryptBytes(raw);
            	out.writeUTF("IMAGE");
            	out.writeUTF(file.getName());
            	out.writeUTF(username);
            	out.writeLong(encrypted.length);
            	out.write(encrypted);
            	chatArea.append("Bạn đã gửi ẢNH: " + file.getName() + "\n");
            } catch (IOException e) {
                showError("Lỗi gửi ảnh");
            }
        }
    }

    private void sendStegoImage() {
        fileChooser.setDialogTitle("Chọn ảnh để giấu tin");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Hình ảnh", "jpg", "png", "jpeg"));
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            String hiddenText = JOptionPane.showInputDialog(this, "Nhập nội dung cần giấu trong ảnh:");
            if (hiddenText != null && !hiddenText.isEmpty()) {
                try {
                    File stegoImage = SteganographyUtil.hideText(file, hiddenText);
                    byte[] raw = Files.readAllBytes(stegoImage.toPath());
                    byte[] encrypted = AESUtil.encryptBytes(raw);

                    out.writeUTF("IMAGE");
                    out.writeUTF(stegoImage.getName());
                    out.writeUTF(username);
                    out.writeLong(encrypted.length);
                    out.write(encrypted);

                    chatArea.append("Bạn đã gửi ảnh có giấu tin: " + stegoImage.getName() + "\n");

                } catch (Exception ex) {
                    showError("Lỗi khi giấu tin trong ảnh: " + ex.getMessage());
                }
            }
        }
    }

    private void revealStegoImage() {
        fileChooser.setDialogTitle("Chọn ảnh để giải tin");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Hình ảnh", "png"));
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                String hidden = SteganographyUtil.revealText(file);
                JOptionPane.showMessageDialog(this, "Nội dung giấu trong ảnh:\n" + hidden);
            } catch (Exception e) {
                showError("Lỗi khi giải tin: " + e.getMessage());
            }
        }
    }

    
    private void sendAudio() {
        new Thread(() -> {
            try {
                File outFile = new File("audio.wav");
                AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
                TargetDataLine line = AudioSystem.getTargetDataLine(format);
                line.open(format);
                line.start();
                AudioInputStream ais = new AudioInputStream(line);

                // Ghi âm trong luồng riêng
                Thread recordingThread = new Thread(() -> {
                    try {
                        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                recordingThread.start();

                // Hộp thoại hiển thị cho người dùng nhấn OK để dừng
                JOptionPane.showMessageDialog(this, "Đang ghi âm... Nhấn OK để dừng");

                // Dừng ghi
                line.stop();
                line.close();
                recordingThread.join(); // Chờ ghi xong

                // Gửi file như thường
                byte[] raw = Files.readAllBytes(outFile.toPath());
                byte[] encrypted = AESUtil.encryptBytes(raw);
                out.writeUTF("AUDIO");
                out.writeUTF(outFile.getName());
                out.writeUTF(username);
                out.writeLong(encrypted.length);
                out.write(encrypted);

                chatArea.append("Bạn đã gửi AUDIO: " + outFile.getName() + "\n");


            } catch (Exception e) {
                showError("Lỗi ghi âm hoặc gửi");
            }
        }).start();
    }


    private void receiveMessages() {
        new Thread(() -> {
            try {
                while (true) {
                    String type = in.readUTF();
                    switch (type) {
                        case "TEXT" -> {
                            String encrypted = in.readUTF();
                            String decrypted = AESUtil.decrypt(encrypted);
                            chatArea.append(decrypted + "\n");
                        }
                        case "FILE", "IMAGE", "AUDIO" -> receiveFile(type);
                        case "VANISH" -> {
                            String encrypted = in.readUTF();
                            int seconds = in.readInt();
                            String decrypted = AESUtil.decrypt(encrypted);

                            SwingUtilities.invokeLater(() -> {
                                JLabel label = new JLabel(decrypted);
                                chatArea.append("[Tự hủy] " + decrypted + "\n");

                                // Tạo timer xóa sau N giây
                                new Timer(seconds * 1000, e -> {
                                    chatArea.setText(chatArea.getText().replace("[Tự hủy] " + decrypted + "\n", ""));
                                }).start();
                            });
                        }
                    }
                }
            } catch (IOException e) {
                showError("Đã ngắt kết nối server");
            }
        }).start();
    }

    private void receiveFile(String type) throws IOException {
        String fileName = in.readUTF();
        String sender = in.readUTF();
        long size = in.readLong();
        byte[] encryptedData = new byte[(int) size];
        in.readFully(encryptedData);

        // Giải mã nếu là FILE, IMAGE hoặc AUDIO
        byte[] decryptedData = AESUtil.decryptBytes(encryptedData);

        File received = new File("received_" + fileName);
        Files.write(received.toPath(), decryptedData);
        chatArea.append(sender + " gửi " + type + ": " + fileName + "\n");

        JButton btn = new JButton("📥 Tải: " + fileName);
        btn.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(received);
            } catch (IOException ex) {
                showError("Không mở được file");
            }
        });
        mediaPanel.add(btn);
        mediaPanel.revalidate();
    }


    private void showEmojiPopup() {
        JPopupMenu menu = new JPopupMenu();
        String[] emojis = {"😀", "😂", "❤️", "👍", "🎉","👻"};
        for (String emoji : emojis) {
            JMenuItem item = new JMenuItem(emoji);
            item.addActionListener(e -> inputField.setText(inputField.getText() + emoji));
            menu.add(item);
        }
        menu.show(emojiBtn, 0, emojiBtn.getHeight());
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String name = JOptionPane.showInputDialog("Nhập tên người dùng:");
            if (name != null && !name.trim().isEmpty()) {
                new EnhancedChatClient(name);
            }
        });
    }
}
