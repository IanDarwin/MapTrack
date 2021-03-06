package com.darwinsys.maptrack.fileio; // SHADED FROM jpstrack.fileio

public class FileNameUtils {
	
	private static final String FILENAME_FORMAT = "YYYYMMDDHHMM";
	public static final String GPX_FILENAME_EXTENSION = "gpx";
	private final static String FILENAME_PRINTF_TEMPLATE = "%1$tY%1$tm%1$td%1$tH%1$tM";

	// This may become a facility for interpreting filename formats
	//	private static Map<String,String> formatMap = new HashMap<String,String>();
	//	static {
	//		formatMap.put("YYYY", "$tY");
	//	}

	public static String getDefaultFilenameFormat() {
		return FILENAME_FORMAT;		
	}

	public static String getDefaultFilenameFormatWithExt() {
		return FILENAME_FORMAT + "." + GPX_FILENAME_EXTENSION;
	}

	public static String getNextFilename() {
		return getNextFilenamePrefix() + "." + GPX_FILENAME_EXTENSION;
	}
	
	/** Return the next file name; given the suffix (which should NOT include
	 * a leading '.', since we provide that!)
	 * @param suffix The filename extention sans '.', e.g., "mp3"
	 * @return The filename, e.g., foo.mp3
	 */
	public static String getNextFilename(String suffix) {
		return getNextFilenamePrefix() + "." + suffix;
	}
	public static String getNextFilenamePrefix() {
		return String.format(FILENAME_PRINTF_TEMPLATE, System.currentTimeMillis());		
	}
}
