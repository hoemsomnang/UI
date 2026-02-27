package application.instagram.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DateUtils {
	public final static String FORMAT_YEAR = "yyyy";
	public final static String FORMAT_MONTH = "MM";
	public final static String FORMAT_DAY = "dd";
	public final static String FORMAT_DATE = "yyyyMMdd";
	public final static String FORMAT_DATETIME = "yyyyMMddHHmmss";
	public final static String FORMAT_FULL_DATETIME = "yyyyMMddHHmmssSSS";
	public final static String FORMAT_TIME = "HHmmss";
	public final static String FORMAT_TIME_TYPE1 = "HH:mm:ss";
	public final static String FORMAT_TIMESTAMP = "HHmmssSSS";
	public final static String FORMAT_HOUR = "HH";
	public final static String FORMAT_MINUTE = "mm";
	public final static String FORMAT_SECOND = "ss";
	public final static String FORMAT_MILLISECOND = "SSS";
	public final static String FORMAT_DATE_TYPE1 = "yyyy-MM-dd";
	public final static String FORMAT_FILE_LOG_TYPE1 = "yyyyMMddaa";
	public final static String FORMAT_FILE_LOG_TYPE2 = "yyyyMMddHH";
	public final static String FORMAT_TIMESTAMP_TYPE1 = "yyyyMMddHHmm";
	
	
	public static String getCurrentFormatDate( String format ) {
		SimpleDateFormat sdf = new SimpleDateFormat( format, new Locale("en", "USD"));
		return sdf.format(Calendar.getInstance().getTime());
	}
}
