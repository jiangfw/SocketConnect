package com.carrobot.android.socketconnect.utils;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @Description 日志输出类
 * @ClassName: LogController
 * @author jiangfuwei
 * @date 2017年1月5日 下午1:16:37
 * 
 */
public class LogController {
	
	static{
		if (Config.isDebugMode){
			LOG_ENABLE = true;
			DETAIL_ENABLE = true;
		}

	}

	private static boolean LOG_ENABLE = true;
	private static  boolean DETAIL_ENABLE = true;

	private static final String strLogDirPath = FileCache.getInstance().getJsonPath();
	private static final SimpleDateFormat sdObj1 = new SimpleDateFormat("yyyy-MM-dd");
	private static final SimpleDateFormat sdObj2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private static String buildMsg(String msg) {
		StringBuilder buffer = new StringBuilder();

		if (DETAIL_ENABLE) {
			final StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[4];

			buffer.append("[");
//			buffer.append(Thread.currentThread().getName());
//			buffer.append(": ");
			buffer.append(stackTraceElement.getFileName());
			buffer.append(": ");
			buffer.append(stackTraceElement.getLineNumber());
//			buffer.append(": ");
//			buffer.append(stackTraceElement.getMethodName());
			buffer.append("]");
		}

		buffer.append("_");

		buffer.append(msg);

		return buffer.toString();
	}

	public static void d(String tag, String msg) {
		if (LOG_ENABLE) {
			Log.d(tag, buildMsg(msg));
			print(tag,buildMsg(msg));

		}
	}

	public static void i(String tag, String msg) {
		if (LOG_ENABLE) {
			Log.i(tag, buildMsg(msg));
			print(tag,buildMsg(msg));
		}
	}

	public static void print(String strLog) {
		if (!LOG_ENABLE) {
			return;
		}
		if ((strLog == null) || ("".equals(strLog.trim()))) {
			return;
		}
		File fileDir = new File(strLogDirPath);
		if (!fileDir.exists()) {
			fileDir.mkdir();
		}
		Date date = new Date();
		String strFileName = strLogDirPath + "/log"  + ".txt";
		String strContent = sdObj2.format(date) + ": " + strLog.trim() + "\r\n";
		FileWriter fw = null;
		try {
			fw = new FileWriter(strFileName, true);
			fw.write(strContent);
		} catch (IOException e) {
			e.printStackTrace();

			if (fw != null) {
				try {
					fw.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		} finally {
			if (fw != null) {
				try {
					fw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void print(String fileName, String strLog) {
		if (!LOG_ENABLE) {
			return;
		}
		if ((strLog == null) || ("".equals(strLog.trim()))) {
			return;
		}
		File fileDir = new File(strLogDirPath);
		if (!fileDir.exists()) {
			fileDir.mkdir();
		}
		Date date = new Date();
		String strFileName = strLogDirPath + "/" + fileName  + ".txt";
		String strContent = sdObj2.format(date) + ": " + strLog.trim() + "\r\n";
		FileWriter fw = null;
		try {
			fw = new FileWriter(strFileName, true);
			fw.write(strContent);
		} catch (IOException e) {
			e.printStackTrace();

			if (fw != null) {
				try {
					fw.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		} finally {
			if (fw != null) {
				try {
					fw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
