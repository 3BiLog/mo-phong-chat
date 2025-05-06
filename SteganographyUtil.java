package mo_phong_zalo2;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class SteganographyUtil {
	public static File hideText(File imageFile, String message) throws Exception {
	    BufferedImage image = ImageIO.read(imageFile);

	    // Mã hóa bằng AES
	    String encryptedMessage = AESUtil.encrypt(message);
	    byte[] msgBytes = encryptedMessage.getBytes("UTF-8");

	    // Biến byte[] thành chuỗi bit
	    StringBuilder bitStr = new StringBuilder();
	    for (byte b : msgBytes) {
	        bitStr.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
	    }

	    // Thêm byte kết thúc 00000000
	    bitStr.append("00000000");

	    int width = image.getWidth();
	    int height = image.getHeight();
	    int bitIndex = 0;

	    outer:
	    for (int y = 0; y < height; y++) {
	        for (int x = 0; x < width; x++) {
	            if (bitIndex >= bitStr.length()) break outer;

	            int rgb = image.getRGB(x, y);
	            int r = (rgb >> 16) & 0xFF;
	            int g = (rgb >> 8) & 0xFF;
	            int b = rgb & 0xFF;

	            // Ghi 1 bit vào LSB của red
	            r = (r & 0xFE) | (bitStr.charAt(bitIndex++) - '0');

	            int newRGB = (r << 16) | (g << 8) | b;
	            image.setRGB(x, y, newRGB);
	        }
	    }

	    File out = new File("stego_" + imageFile.getName());
	    ImageIO.write(image, "png", out);
	    return out;
	}

    
	public static String revealText(File imageFile) throws Exception {
	    BufferedImage image = ImageIO.read(imageFile);
	    StringBuilder bits = new StringBuilder();

	    int width = image.getWidth();
	    int height = image.getHeight();

	    for (int y = 0; y < height; y++) {
	        for (int x = 0; x < width; x++) {
	            int rgb = image.getRGB(x, y);
	            int r = (rgb >> 16) & 0xFF;
	            bits.append(r & 1);
	        }
	    }

	    // Chuyển dãy bit thành byte[]
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    for (int i = 0; i + 8 <= bits.length(); i += 8) {
	        String byteStr = bits.substring(i, i + 8);
	        int charCode = Integer.parseInt(byteStr, 2);
	        if (charCode == 0) break; // null byte => kết thúc
	        baos.write(charCode);
	    }

	    String encrypted = baos.toString("UTF-8");
	    return AESUtil.decrypt(encrypted);
	}

}
