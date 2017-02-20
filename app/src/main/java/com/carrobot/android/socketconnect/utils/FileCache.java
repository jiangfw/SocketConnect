package com.carrobot.android.socketconnect.utils;


import android.os.Environment;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;


public class FileCache {
	private static FileCache fileCache; // 本类的引用
	private String strJsonPath;// Json保存的路径
	private String romPath;


	private List<String> mSdcard2Paths = new LinkedList<String>();

	private FileCache() {
		// 初始化sd卡路径
		initSdcard2Paths();
		// 设置存储路径
		String strPathHead = null;
		if (!getValidSdCardPath().equalsIgnoreCase("")) {
			strPathHead = getValidSdCardPath();
		} else {
			strPathHead = "/data/data/com.carrobot.android.socketclient";
		}
		strJsonPath = strPathHead + "/ileja/json";
		romPath = strPathHead+"/ileja/rom";

		File jsonFileDirs = new File(strJsonPath);

		if (!jsonFileDirs.exists()) {
			jsonFileDirs.mkdirs();
		}

		File romFileDirs = new File(romPath);
		if(!romFileDirs.exists()){
			romFileDirs.mkdirs();
		}
	}

	public static FileCache getInstance() {
		if (null == fileCache) {
			fileCache = new FileCache();
		}
		return fileCache;
	}

	public String getJsonPath(){
		return strJsonPath;
	}

	public String getRomPath(){
		return romPath;
	}

	private void initSdcard2Paths() {
		String extFileStatus = Environment.getExternalStorageState();
		File extFile = Environment.getExternalStorageDirectory();
		mSdcard2Paths.clear();
		if (extFileStatus.endsWith(Environment.MEDIA_MOUNTED) && extFile.exists() && extFile.isDirectory() && extFile.canWrite()) {
			mSdcard2Paths.add(extFile.getAbsolutePath());
		}
		StringBuilder builder = new StringBuilder();
		try {
			// obtain executed result of command line code of 'mount', to judge
			// whether tfCard exists by the result
			Runtime runtime = Runtime.getRuntime();
			Process process = runtime.exec("mount");
			InputStream is = process.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			String line = null;
			int mountPathIndex = 1;
			while ((line = br.readLine()) != null) {
				// format of sdcard file system: vfat/fuse /storage/uicc0
				builder.append(line).append("\n");
				if ((!line.contains("fat") && !line.contains("fuse") && !line.contains("storage")) || line.contains("uicc0") || line.contains("secure") || line.contains("asec")
						|| line.contains("firmware") || line.contains("shell") || line.contains("obb") || line.contains("legacy") || line.contains("data")) {
					continue;
				}
				String[] parts = line.split(" ");
				int length = parts.length;
				if (mountPathIndex >= length) {
					continue;
				}
				String mountPath = parts[mountPathIndex];
				if (!mountPath.contains("/") || mountPath.contains("data") || mountPath.contains("Data")) {
					continue;
				}
				File mountRoot = new File(mountPath);
				if (!mountRoot.exists() || !mountRoot.isDirectory() || !mountRoot.canWrite()) {
					continue;
				}

				long spaceLeft = 0;
				try {
					StatFs statFs = new StatFs(mountPath);
					int avCounts = statFs.getBlockCount();
					long blockSize = statFs.getBlockSize();
					spaceLeft = avCounts * blockSize;
				} catch (Exception e) {
				}

				if (spaceLeft == 0) {
					continue;
				}

				boolean equalsToPrimarySD = mountPath.equals(extFile.getAbsolutePath());
				if (equalsToPrimarySD) {
					continue;
				}
				if (mSdcard2Paths.contains(mountPath)) {
					continue;
				}
				mSdcard2Paths.add(mountPath);
			}
			is.close();
			isr.close();
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 获取有效的SD卡
	 * 
	 * @return
	 */
	public String getValidSdCardPath() {
		if (mSdcard2Paths.size() != 0) {
			return mSdcard2Paths.get(0);
		} else {
			return "";
		}
	}
}
