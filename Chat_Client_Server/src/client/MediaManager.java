package client;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Ghi âm tin nhắn thoại, gọi thoại / video realtime qua UDP. */
public class MediaManager implements UdpMedia.Listener {
    public static final AudioFormat FMT = new AudioFormat(16000f, 16, 1, true, false);

    private final UdpMedia udp;
    private final String me;
    private final Consumer<String> onError;
    private volatile boolean audioOn, videoOn, recording;
    private final Map<String, SourceDataLine> speakers = new ConcurrentHashMap<>();
    private final Map<String, JLabel> videoLabels = new ConcurrentHashMap<>();

    public MediaManager(UdpMedia udp, String me, Consumer<String> onError) {
        this.udp = udp;
        this.me = me;
        this.onError = onError;
        udp.startReceiver(this);
    }

    // ---------- Gọi thoại (UDP) ----------

    public void startAudio(String recipients) {
        if (audioOn) return;
        audioOn = true;
        Thread t = new Thread(() -> {
            TargetDataLine mic = null;
            try {
                mic = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, FMT));
                mic.open(FMT);
                mic.start();
                byte[] buf = new byte[1024];
                while (audioOn) {
                    int n = mic.read(buf, 0, buf.length);
                    if (n > 0) udp.send(UdpMedia.AUDIO, me, recipients, Arrays.copyOf(buf, n));
                }
            } catch (Exception e) {
                audioOn = false;
                onError.accept("Không mở được micro: " + e.getMessage());
            } finally {
                if (mic != null) {
                    mic.stop();
                    mic.close();
                }
            }
        }, "audio-send");
        t.setDaemon(true);
        t.start();
    }

    public void stopAudio() {
        audioOn = false;
    }

    // ---------- Video realtime (UDP, chia sẻ màn hình) ----------

    public void startVideo(String recipients) {
        if (videoOn) return;
        videoOn = true;
        Thread t = new Thread(() -> {
            try {
                Robot robot = new Robot();
                Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                int w = 480, h = Math.max(1, screen.height * w / screen.width);
                while (videoOn) {
                    long start = System.currentTimeMillis();
                    BufferedImage shot = robot.createScreenCapture(screen);
                    BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = small.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(shot, 0, 0, w, h, null);
                    g.dispose();
                    float q = 0.5f;
                    byte[] jpg = jpeg(small, q);
                    while (jpg.length > 50000 && q > 0.1f) {
                        q -= 0.1f;
                        jpg = jpeg(small, q);
                    }
                    udp.send(UdpMedia.VIDEO, me, recipients, jpg);
                    long sleep = 125 - (System.currentTimeMillis() - start); // ~8 fps
                    if (sleep > 0) Thread.sleep(sleep);
                }
            } catch (Exception e) {
                videoOn = false;
                onError.accept("Lỗi video: " + e.getMessage());
            }
        }, "video-send");
        t.setDaemon(true);
        t.start();
    }

    public void stopVideo() {
        videoOn = false;
    }

    private static byte[] jpeg(BufferedImage img, float quality) throws Exception {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(quality);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(b)) {
            w.setOutput(ios);
            w.write(null, new IIOImage(img, null, null), p);
        }
        w.dispose();
        return b.toByteArray();
    }

    // ---------- Nhận ----------

    @Override
    public void onMedia(int type, String sender, byte[] payload) {
        if (type == UdpMedia.AUDIO) {
            try {
                SourceDataLine line = speakers.computeIfAbsent(sender, s -> {
                    try {
                        SourceDataLine l = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, FMT));
                        l.open(FMT, 8192);
                        l.start();
                        return l;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                if (line.available() >= payload.length) line.write(payload, 0, payload.length);
            } catch (Exception e) {
                // không có loa
            }
        } else if (type == UdpMedia.VIDEO) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(payload));
                if (img != null) SwingUtilities.invokeLater(() -> showFrame(sender, img));
            } catch (Exception ignored) {
            }
        }
    }

    private void showFrame(String sender, BufferedImage img) {
        JLabel label = videoLabels.get(sender);
        if (label == null) {
            label = new JLabel();
            JFrame f = new JFrame("Video từ " + sender);
            f.add(label);
            f.setSize(img.getWidth() + 16, img.getHeight() + 40);
            f.setLocationByPlatform(true);
            f.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    videoLabels.remove(sender);
                }
            });
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.setVisible(true);
            videoLabels.put(sender, label);
        }
        label.setIcon(new ImageIcon(img));
    }

    // ---------- Ghi âm tin nhắn thoại (gửi qua TCP) ----------

    private TargetDataLine recLine;
    private final ByteArrayOutputStream recBuf = new ByteArrayOutputStream();

    public boolean isRecording() {
        return recording;
    }

    public void startRecording() throws Exception {
        recLine = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, FMT));
        recLine.open(FMT);
        recLine.start();
        recBuf.reset();
        recording = true;
        Thread t = new Thread(() -> {
            byte[] buf = new byte[2048];
            while (recording) {
                int n = recLine.read(buf, 0, buf.length);
                if (n > 0) recBuf.write(buf, 0, n);
            }
        }, "recorder");
        t.setDaemon(true);
        t.start();
    }

    /** @return dữ liệu WAV hoặc null nếu quá ngắn. */
    public byte[] stopRecording() throws Exception {
        recording = false;
        Thread.sleep(100);
        recLine.stop();
        recLine.close();
        byte[] pcm = recBuf.toByteArray();
        if (pcm.length < FMT.getFrameSize() * 4000) return null; // < 0.25s
        AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(pcm), FMT, pcm.length / FMT.getFrameSize());
        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wav);
        return wav.toByteArray();
    }

    public void shutdown() {
        audioOn = false;
        videoOn = false;
        recording = false;
        udp.close();
    }
}
