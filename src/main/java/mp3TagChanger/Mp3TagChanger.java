package mp3TagChanger;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

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

public class Mp3TagChanger {

	public static File saveDir;
	private static long targets = -1;
	
	private static boolean failedFlag = false;
	
	private static String artistDelimiter = "-";
	private static int artistIndex = 0;
	
	
	private static boolean verbose = false;
	private static boolean overwrite = false;
	private static AtomicLong cnt = new AtomicLong(0L);
	private static JLabel loadingStatus;
	private static JProgressBar progress;
	private static JFrame loadingFrame;
	
	
	public static void main(String[] args) throws InvocationTargetException, InterruptedException {

		if(args.length != 0) {
			for(String str : args) {
				if(str.startsWith("--artistDelimiter=")) {
					artistDelimiter = str.split("=")[1];
				} else if(str.startsWith("--artistIndex=")) {
					artistIndex = Integer.parseInt(str.split("=")[1]);
				} else if(str.startsWith("--verbose=")) {
					verbose = Boolean.parseBoolean(str.split("=")[1]);
				} else if(str.startsWith("--overwrite=")) {
					overwrite = Boolean.parseBoolean(str.split("=")[1]);
				}
			}
		}
			
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
		
		if(verbose) {
			showProgress();
		}
		
		Arrays.stream(flist).parallel().filter(File::isFile).filter(Mp3TagChanger::isMp3).forEach(Mp3TagChanger::setTag);
		
		if (!failedFlag) {
			SwingUtilities.invokeAndWait(() -> {
				final JDialog dialog = new JDialog();
				dialog.setAlwaysOnTop(true);
				JOptionPane.showMessageDialog(dialog,
						"Changed files are in following folder :\n" + saveDir.getAbsolutePath(), "done!",
						JOptionPane.INFORMATION_MESSAGE);
				dialog.dispose();
			});
		}
		
		if (verbose) {
			loadingFrame.setVisible(false);
			loadingFrame.dispose();
		}
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
		long cntNow = cnt.get();
		loadingStatus.setText(String.format("%0" + String.valueOf(targets).length() + "d/%" + String.valueOf(targets).length() + "d", cntNow, targets));
		progress.setValue((int) (100.0 * cntNow / targets));
	}

	private synchronized static void setFailedFlag() {
		failedFlag = true;
	}
	
	private static void setTag(File f) { 
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
			try {
				if(overwrite || id3v2Tag.getArtist() == null) id3v2Tag.setArtist(getArtist(f.getName()));
			} catch (StringIndexOutOfBoundsException ae) {
				id3v2Tag.setArtist(".");
			}
			if(overwrite || id3v2Tag.getTitle() == null) id3v2Tag.setTitle(getTitle(f.getName()));
			if(overwrite || id3v2Tag.getAlbum() == null) id3v2Tag.setAlbum(getDefaultAlbum());
			mp3file.save(saveDir.getAbsolutePath() + File.separator + f.getName());
		} catch (Exception e) {
			setFailedFlag();
			e.printStackTrace();
			SwingUtilities.invokeLater(() -> {
				final JDialog dialog = new JDialog();
				dialog.setAlwaysOnTop(true);
				JOptionPane.showMessageDialog(dialog, e.getMessage(), "Error! : " + f.getName(), JOptionPane.ERROR_MESSAGE);
				dialog.dispose();
			});
		} finally {
			if(verbose) {
				cnt.incrementAndGet();
				SwingUtilities.invokeLater(Mp3TagChanger::updateUI);
			}
		}
	}
	
	private static String getTitle(String fileName) { return fileName.substring(0, fileName.lastIndexOf(".")); }
	private static String getArtist(String fileName) { return (fileName.contains(artistDelimiter)) ? fileName.split(artistDelimiter)[artistIndex] : "."; } 
	private static String getDefaultAlbum() { return "."; }
	
	private static boolean isMp3(File f) {
		return f.getName().endsWith(".mp3");
	}
	
}
