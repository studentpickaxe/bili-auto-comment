package yfrp.autobili.util;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.detector.Detector;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class QrCodeUtil {

    /**
     * 将 Base64 编码的 PNG 图片转换为 BufferedImage 对象
     *
     * @param imgBase64 Base64 编码的 PNG 图片字符串
     * @return BufferedImage 对象
     * @throws IOException 转换过程中发生错误
     */
    public static BufferedImage base64ToImage(String imgBase64)
            throws IOException {

        if (!imgBase64.startsWith("data:image/png;base64,")) {
            throw new IllegalArgumentException("仅支持 PNG 格式的 Base64 图片");
        }

        String base64 = imgBase64.substring("data:image/png;base64,".length());
        byte[] imgBytes = Base64.getDecoder().decode(base64);

        return ImageIO.read(new ByteArrayInputStream(imgBytes));
    }

    /**
     * 将 BufferedImage 对象转换为 Base64 编码的 PNG 图片字符串
     *
     * @param image BufferedImage 对象
     * @return Base64 编码的 PNG 图片字符串
     * @throws IOException 转换过程中发生错误
     */
    public static String imageToBase64(BufferedImage image)
            throws IOException {

        var outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        byte[] imgBytes = outputStream.toByteArray();

        return "data:image/png;base64," + Base64.getEncoder().encodeToString(imgBytes);
    }

    /**
     * 打印 Base64 编码的 PNG 图片中的二维码字符画
     *
     * @param imgBase64 Base64 编码的 PNG 图片字符串
     */
    public static void printQrCode(String imgBase64) {

        try {
            BufferedImage image = base64ToImage(imgBase64);
            printQrCode(image);

        } catch (IOException e) {
            IO.println("无法解码 Base64 图片: " + e.getMessage());
        }

    }

    /**
     * 打印 BufferedImage 中的二维码字符画
     *
     * @param image BufferedImage 对象
     */
    public static void printQrCode(BufferedImage image) {

        try {
            var qrCodeStrArray = extractQrCode(image);
            for (String line : qrCodeStrArray) {
                IO.println(line);
            }

        } catch (NotFoundException | FormatException e) {
            IO.println("无法提取二维码: " + e.getMessage());
        }

    }

    /**
     * 从 BufferedImage 中提取二维码并转换为字符画形式
     *
     * @param image BufferedImage 对象
     * @return 字符画形式的二维码数组
     */
    public static String[] extractQrCode(BufferedImage image)
            throws NotFoundException,
                   FormatException {

        // 获取标准化的模块矩阵 (Matrix of Modules)
        DetectorResult detectorResult = new Detector(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))).getBlackMatrix()).detect();
        BitMatrix matrix = detectorResult.getBits();

        int width = matrix.getWidth();
        int height = matrix.getHeight();

        // 上下静区，两行并一行，结果向上取整
        int resultHeight = (height + 3) / 2;
        String[] result = new String[resultHeight];

        for (int i = 0; i < resultHeight; i++) {
            int l = resultHeight - i - 1;
            int yTop = height - i * 2 - 1;
            int yBottom = height - i * 2;

            StringBuilder row = new StringBuilder(width + 2);
            row.append(' '); // 左静区

            for (int x = 0; x < width; x++) {
                boolean top = (yTop >= 0) &&
                              matrix.get(x, yTop);
                boolean bottom = (yBottom >= 0 && yBottom < height) &&
                                 matrix.get(x, yBottom);

                if (top && bottom) {
                    row.append("█"); // 上下全实
                } else if (top) {
                    row.append("▀"); // 上实下空
                } else if (bottom) {
                    row.append("▄"); // 上空下实
                } else {
                    row.append(" "); // 上下全空
                }
            }

            row.append(' '); // 右静区
            result[l] = row.toString();
        }

        return result;
    }

    /**
     * 为 Base64 编码的 PNG 图片添加白色边框
     *
     * @param imgBase64  Base64 编码的 PNG 图片字符串
     * @param borderSize 边框大小（像素）
     * @return 添加边框后的 Base64 编码 PNG 图片字符串
     */
    public static String addWhiteBorder(String imgBase64,
                                        int borderSize) {

        try {
            BufferedImage originalImage = base64ToImage(imgBase64);

            // 计算尺寸
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            int newWidth = originalWidth + borderSize * 2;
            int newHeight = originalHeight + borderSize * 2;

            // 创建新画布
            BufferedImage newImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);

            // 填充白色边框
            var backgroundRgb = originalImage.getRGB(0, 0);
            drawRectangle(newImage, (0), (0), newWidth, borderSize, backgroundRgb);
            drawRectangle(newImage, (0), (newHeight - borderSize), newWidth, borderSize, backgroundRgb);
            drawRectangle(newImage, (0), borderSize, borderSize, originalHeight, backgroundRgb);
            drawRectangle(newImage, (newWidth - borderSize), borderSize, borderSize, originalHeight, backgroundRgb);

            // 复制原图像素
            var xMax = originalImage.getHeight();
            var yMax = originalImage.getWidth();
            for (int y = 0; y < yMax; y++) {
                var y2 = y + borderSize;
                for (int x = 0; x < xMax; x++) {
                    var x2 = x + borderSize;

                    int rgb = originalImage.getRGB(x, y);
                    newImage.setRGB(x2, y2, rgb);
                }
            }

            return imageToBase64(newImage);

        } catch (IOException _) {
            return imgBase64;
        }
    }

    private static void drawRectangle(BufferedImage image,
                                      int x,
                                      int y,
                                      int width,
                                      int height,
                                      int rgb) {

        var xMax = x + width;
        var yMax = y + height;
        for (int j = y; j < yMax; j++) {
            for (int i = x; i < xMax; i++) {
                image.setRGB(i, j, rgb);
            }
        }
    }

}
