package mp3TagChanger;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.stream.Collector;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v23Tag;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.NotSupportedException;

public class Mp3TagChanger {

	public static File saveDir;
	private static long targets = -1;
	
	private static boolean failedFlag = false;
	
	private static String artistDelimiter = "-";
	private static int artistIndex = 0;
	
	private static BiConsumer<ID3v2, File> tagChanger = null;
	
	private static final char[] stringArray = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvxyz0123456789".toCharArray();
	
	private static boolean overwrite = false;
	private static long cnt = 0L;
	private static JLabel loadingStatus;
	private static JProgressBar progress;
	private static JFrame loadingFrame;
	
	
	public static void main(String[] args) throws InvocationTargetException, InterruptedException {

		if(args.length != 0) {
			for(String str : args) {
				if(str.startsWith("--artistIndex=")) {
				artistIndex = Integer.parseInt(str.split("=")[1]);
				} else if(str.startsWith("--artistDelimiter=")) {
					if(str.split("=").length != 2 || (artistDelimiter = str.split("=")[1]) == null) {
						System.err.println("Invalid use of artistDelimiter : " + str);
						System.err.println();
						showHelp();
						return;
					}
				} else if(str.equals("--overwrite")) {
					overwrite = true;
				} else if(str.equals("--random")) {
					tagChanger = Mp3TagChanger::randomTagChanger;
				} else if(str.equals("--help")) {
					showHelp();
					return;
				} else {
					System.err.println("Invalid command : " + str);
					System.err.println();
					showHelp();
					return;
				}
			}
		}
		
		if(tagChanger == null) tagChanger = Mp3TagChanger::defualtTagChanger;
		
		JFileChooser jfc = new JFileChooser();
		jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		jfc.setDialogTitle("Choose directory that has music!");
		
		if(jfc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) { return; }
		
		File location = jfc.getSelectedFile();
		
		jfc.setCurrentDirectory(location.getParentFile());
		jfc.setDialogTitle("Choose directory to save music!");
		
		if(jfc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) { return; }
		
		saveDir = jfc.getSelectedFile();
		
		File[] flist = location.listFiles();
		if(flist == null) {System.err.println("Folder " + location.getAbsolutePath() + " is empty!"); return;}
		
		targets = Arrays.stream(flist).parallel().filter(File::isFile).filter(Mp3TagChanger::isMp3).count();
		
		SwingUtilities.invokeLater(Mp3TagChanger::showProgress);
		
		Instant startTime = Instant.now();
		Arrays.stream(flist).parallel().filter(File::isFile).filter(Mp3TagChanger::isMp3).map(Mp3TagChanger::setTag).filter(Objects::nonNull)
			.forEach(f -> {
				try {
					f.save(saveDir.getAbsolutePath() + File.separator + f.getFilename().substring(f.getFilename().lastIndexOf(File.separator) + 1));
				} catch (NotSupportedException | IOException e) {
					SwingUtilities.invokeLater(() -> {
						final JDialog dialog = new JDialog();
						dialog.setAlwaysOnTop(true);
						JOptionPane.showMessageDialog(dialog, e.getMessage(), "Save Error! : " + f.getFilename(), JOptionPane.ERROR_MESSAGE);
						dialog.dispose();
					});
				}
			});
		Duration diff = Duration.between(startTime, Instant.now());
		
		if (!failedFlag) {
			SwingUtilities.invokeAndWait(() -> {
				final JDialog dialog = new JDialog();
				dialog.setAlwaysOnTop(true);
				JOptionPane.showMessageDialog(dialog,
						"Task done in " + String.format("%d min %d sec", diff.toMinutes(), diff.toSecondsPart())
						+ "\nChanged files are in following folder :\n" + saveDir.getAbsolutePath(), "done!",
						JOptionPane.INFORMATION_MESSAGE);
				dialog.dispose();
			});
		}
		
		SwingUtilities.invokeLater(() -> {
			loadingFrame.setVisible(false);
			loadingFrame.dispose();
		});
	}

	public static void showHelp() {
		System.out.println("Usage : java -jar mp3TagChanger.jar [options]");
		System.out.println("Options : ");
		System.out.println("\t--help\tShow this help message\n");
		System.out.println("\t--artistIndex=<index>\tIndex of artist name in name of mp3 file");
		System.out.println("\t\t\t\tif artist name comes before song name, value should be 0(default). ex)\"The Beatles - Let it be.mp3\"");
		System.out.println("\t\t\t\tif song name comes before artist name, value should be 1. ex)\"Let it be - The Beatles.mp3\"\n");
		System.out.println("\t--artistDelimiter=<delimiter>\tSet delimiter between song name and artist.");
		System.out.println("\t\t\t\t\tDefault is \"-\", so mp3 file name would be like : \"The Beatles - Let it be.mp3\"\n");
		System.out.println("\t--overwrite\tOvewrite existing mp3 tag(title, artist, album)\n");
		System.out.println("\t--random\tSet mp3 tag(title, artist, album) to random meaningless text\n");
	}
	
	private static void showProgress() {

		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		loadingFrame = new JFrame();
		loadingFrame.setTitle("Progress...");
		loadingFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		loadingFrame.setSize(420, 100);
		loadingFrame.setLocation(dim.width/2-loadingFrame.getSize().width/2, dim.height/2-loadingFrame.getSize().height/2);
		loadingFrame.setLayout(null);
		loadingFrame.setResizable(false);
		
		loadingStatus = new JLabel(String.format("%0" + String.valueOf(targets).length() + "d/%" + String.valueOf(targets).length() + "d", 0, targets));
		loadingStatus.setBounds(14, 8, 370, 18);
		
		progress = new JProgressBar();
		progress.setStringPainted(true);
		progress.setBounds(15, 27, 370, 18);
		
		loadingFrame.add(loadingStatus);
		loadingFrame.add(progress);
		loadingFrame.setVisible(true);
		
	}
	private static void updateUI() {
		loadingStatus.setText(String.format("%0" + String.valueOf(targets).length() + "d/%" + String.valueOf(targets).length() + "d", ++cnt, targets));
		progress.setValue((int) (100.0 * cnt / targets));
	}

	private synchronized static void setFailedFlag() {
		failedFlag = true;
	}
	
	private static Mp3File setTag(File f) { 
		try {
			Mp3File mp3file = new Mp3File(f); //https://stackoverflow.com/questions/32820663/changing-the-title-property-of-mp3-file-using-java-api
			ID3v2 id3v2Tag;
			if (mp3file.hasId3v2Tag()) {
				id3v2Tag = mp3file.getId3v2Tag();
			} else {
				// mp3 does not have an ID3v2 tag, let's create one..
				id3v2Tag = new ID3v23Tag();
				mp3file.setId3v2Tag(id3v2Tag);
			}
			tagChanger.accept(id3v2Tag, f);
			return mp3file;
		} catch (Exception e) {
			setFailedFlag();
			e.printStackTrace();
			SwingUtilities.invokeLater(() -> {
				final JDialog dialog = new JDialog();
				dialog.setAlwaysOnTop(true);
				JOptionPane.showMessageDialog(dialog, e.getMessage(), "Tag change Error! : " + f.getName(), JOptionPane.ERROR_MESSAGE);
				dialog.dispose();
			});
			return null;
		} finally {
			SwingUtilities.invokeLater(Mp3TagChanger::updateUI);
		}
	}
	
	
	private static void defualtTagChanger(ID3v2 tag, File f) {
		try {
			if(overwrite || tag.getArtist() == null) tag.setArtist(getArtist(f.getName()));
		} catch (StringIndexOutOfBoundsException ae) {
			tag.setArtist(".");
		}
		if(overwrite || tag.getTitle() == null) tag.setTitle(getTitle(f.getName()));
		if(overwrite || tag.getAlbum() == null) tag.setAlbum(f.getName());
	}
	
	private static void randomTagChanger(ID3v2 tag, File f) {
		if(overwrite || tag.getArtist() == null) tag.setArtist(generateRandomString());
		if(overwrite || tag.getTitle() == null) tag.setTitle(generateRandomString());
		if(overwrite || tag.getAlbum() == null) tag.setAlbum(generateRandomString());
	}
	
	private static String generateRandomString() {
		return new Random().ints(10L, 0, stringArray.length).mapToObj(i -> stringArray[i]).collect(Collector.of(
			    StringBuilder::new,
			    StringBuilder::append,
			    StringBuilder::append,
			    StringBuilder::toString));
	}
	
	private static String getTitle(String fileName) { return fileName.substring(0, fileName.lastIndexOf(".")); }
	private static String getArtist(String fileName) { return (fileName.contains(artistDelimiter)) ? fileName.split(artistDelimiter)[artistIndex] : "."; } 
	//private static String getDefaultAlbum() { return "."; }
	
	private static boolean isMp3(File f) {
		return f.getName().endsWith(".mp3");
	}
	
}
