package mp3TagChanger;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Optional;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v24Tag;
import com.mpatric.mp3agic.Mp3File;

public class Mp3TagChanger {

	public static File saveDir;
	
	private static boolean failedFlag = false;
	
	private static String artistDelimiter = "-";
	private static int artistIndex = 0;
	
	public static void main(String[] args) throws InvocationTargetException, InterruptedException {

		if(args.length != 0) {
			for(String str : args) {
				if(str.startsWith("--artistDelimiter=")) {
					artistDelimiter = str.split("=")[1];
				} else if(str.startsWith("--artistIndex=")) {
					artistIndex = Integer.parseInt(str.split("=")[1]);
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
		
		Arrays.stream(flist).parallel().filter(File::isFile).filter(Mp3TagChanger::isMp3).forEach(Mp3TagChanger::setTag);
		
		if(failedFlag) return;
		
		SwingUtilities.invokeAndWait(() -> {
			final JDialog dialog = new JDialog();
			dialog.setAlwaysOnTop(true);
			JOptionPane.showMessageDialog(dialog, "Changed files are in following folder :\n" + saveDir.getAbsolutePath(), "done!", JOptionPane.INFORMATION_MESSAGE);
			dialog.dispose();
		});
		
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
				// mp3 does not have an ID3v1 tag, let's create one..
				id3v2Tag = new ID3v24Tag();
				mp3file.setId3v2Tag(id3v2Tag);
			}
			try {
				id3v2Tag.setArtist(getArtist(f.getName()));
			} catch (StringIndexOutOfBoundsException ae) {
				id3v2Tag.setArtist(".");
			}
			id3v2Tag.setTitle(getTitle(f.getName()));
			id3v2Tag.setAlbum(Optional.ofNullable(id3v2Tag.getAlbum()).orElse(getDefaultAlbum()));
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
		}
	}
	
	private static String getTitle(String fileName) { return fileName.substring(0, fileName.lastIndexOf(".")); }
	private static String getArtist(String fileName) { return fileName.split(artistDelimiter)[artistIndex]; }
	private static String getDefaultAlbum() { return "."; }
	
	private static boolean isMp3(File f) {
		return f.getName().endsWith(".mp3");
	}
	
}
