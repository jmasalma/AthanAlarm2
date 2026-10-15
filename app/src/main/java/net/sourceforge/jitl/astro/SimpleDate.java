package net.sourceforge.jitl.astro;

import java.util.GregorianCalendar;

public class SimpleDate {
	int day;
	
	int month;
	
	int year;
	
	/**
	 * Creates a SimpleDate with the specified day, month, and year.
	 * @param day the day of the month
	 * @param month the month (1-12)
	 * @param year the year
	 */
	public SimpleDate(int day, int month, int year) {
		this.day = day;
		this.month = month;
		this.year = year;
	}
		
	/**
	 * Creates a SimpleDate from a GregorianCalendar.
	 * @param gCalendar the GregorianCalendar to convert
	 */
	public SimpleDate(GregorianCalendar gCalendar) {
		this.day = gCalendar.get(GregorianCalendar.DATE);
		this.month = gCalendar.get(GregorianCalendar.MONTH) + 1;
		this.year = gCalendar.get(GregorianCalendar.YEAR);		
	}
	
	public SimpleDate copy() {
		return new SimpleDate(day, month, year);
	}
	
	public int getDay() {
		return day;
	}
	
	public void setDay(int day) {
		this.day = day;
	}
	
	public int getMonth() {
		return month;
	}
	
	public void setMonth(int month) {
		this.month = month;
	}
	
	public int getYear() {
		return year;
	}
	
	public void setYear(int year) {
		this.year = year;
	}
	
}
