package org.ogema.tools.shell.commands;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Parameter;
import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.channelmanager.measurements.BooleanValue;
import org.ogema.core.channelmanager.measurements.DoubleValue;
import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.IntegerValue;
import org.ogema.core.channelmanager.measurements.LongValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.channelmanager.measurements.StringValue;
import org.ogema.core.channelmanager.measurements.Value;
import org.ogema.core.model.Resource;
import org.ogema.core.model.schedule.Schedule;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.model.simple.TimeResource;
import org.ogema.core.timeseries.ReadOnlyTimeSeries;
import org.ogema.core.timeseries.TimeSeries;
import org.osgi.framework.BundleContext;	
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Component(
		service=ScheduleCommands.class,
		property= {
				"osgi.command.scope=schedule",

				"osgi.command.function=addValues",
				"osgi.command.function=getValues",
				"osgi.command.function=printValues",
				"osgi.command.function=firstTimestamp",
				"osgi.command.function=lastTimestamp",
				"osgi.command.function=size",
		}
)
public class ScheduleCommands {
	
	private static final String[] PARSE_FORMATS = {"yyyy-MM-dd'T'HH:mm:ss","yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd'T'HH", "yyyy-MM-dd", "yyyy-MM-dd'T'HH:mmZ", "yyyy-MM-dd'T'HHZ", "yyyy-MM-ddZ"};
	private static final String PRINT_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
	private final CountDownLatch startLatch = new CountDownLatch(1);
	private volatile ServiceRegistration<Application> appService;
	private ApplicationManager appMan;
	

	@Activate
	protected void activate(BundleContext ctx) {
		final Application app = new Application() {

			@Override
			public void start(ApplicationManager appManager) {
				appMan = appManager;
				startLatch.countDown();
			}

			@Override
			public void stop(AppStopReason reason) {
				appMan = null;
			}
			
		};
		this.appService = ctx.registerService(Application.class, app, null);
	}
	
	@Deactivate 
	protected void deactivate() {
		final ServiceRegistration<Application> appService = this.appService;
		if (appService != null) {
			try {
				appService.unregister();
			} catch (Exception ignore) {}
			this.appService = null;
			this.appMan = null;
		} 
				
	}
	
	@Descriptor("Get data points from a timeseries")
	public List<SampledValue> getValues(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Optional limit")
			@Parameter(names= { "-l", "--limit"}, absentValue = "-1")
			int limit,
			@Descriptor("Optional flag indicating whether the first or last values are to be returned, in case a limit is set.")
			@Parameter(names= { "-fb", "--from-beginning"}, absentValue = "false", presentValue="true")
			boolean fromBeginning,
			@Descriptor("Timeseries/Schedule")
			ReadOnlyTimeSeries schedule
			) throws InterruptedException {
		Long start = parseDatetimeWithExpression(startTime);
		Long end = parseDatetimeWithExpression(endTime);
		if (start == null)
			start = Long.MIN_VALUE;
		if (end == null)
			end = Long.MAX_VALUE;
		List<SampledValue> values = schedule.getValues(start, end);
		if (limit >= 0 && values.size() > limit) {
			final List<SampledValue> newValues = new ArrayList<>(limit);
			final int startIdx = fromBeginning ? 0 : values.size() - limit;
			final int endIdx = startIdx + limit;
			for (int idx=startIdx; idx<endIdx; idx++) {
				newValues.add(values.get(idx));
			}
			values = newValues;
		}
		return values;
	}
	
	@Descriptor("Get data points from a timeseries")
	public List<SampledValue> getValues(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Optional limit")
			@Parameter(names= { "-l", "--limit"}, absentValue = "-1")
			int limit,
			@Descriptor("Optional flag indicating whether the first or last values are to be returned, in case a limit is set.")
			@Parameter(names= { "-fb", "--from-beginning"}, absentValue = "false", presentValue="true")
			boolean fromBeginning,
			@Descriptor("A logged value resource")
			SingleValueResource valueResource
			) throws InterruptedException {
		final ReadOnlyTimeSeries schedule = getTimeseries(valueResource);
		if (schedule == null)
			return null;
		return getValues(startTime, endTime, limit, fromBeginning, schedule);
	}
	
	@Descriptor("Get data points from a timeseries")
	public List<SampledValue> getValues(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Optional limit")
			@Parameter(names= { "-l", "--limit"}, absentValue = "-1")
			int limit,
			@Descriptor("Optional flag indicating whether the first or last values are to be returned, in case a limit is set.")
			@Parameter(names= { "-fb", "--from-beginning"}, absentValue = "false", presentValue="true")
			boolean fromBeginning,
			@Descriptor("Schedule resource path")
			String path
			) throws InterruptedException {
		final ReadOnlyTimeSeries r = getTimeseries(path);
		if (r == null)
			return null;
		return getValues(startTime, endTime, limit, fromBeginning, r);
	}
	
	@Descriptor("Print data points from a timeseries")
	public Map<String, SampledValue> printValues(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Optional limit")
			@Parameter(names= { "-l", "--limit"}, absentValue = "-1")
			int limit,
			@Descriptor("Optional flag indicating whether the first or last values are to be returned, in case a limit is set.")
			@Parameter(names= { "-fb", "--from-beginning"}, absentValue = "false", presentValue="true")
			boolean fromBeginning,
			@Descriptor("Timeseries/Schedule")
			ReadOnlyTimeSeries schedule
			) throws InterruptedException {
		final List<SampledValue> values = getValues(startTime, endTime, limit, fromBeginning, schedule);
		if (values== null)
			return null;
		final Map<String, SampledValue> map = new LinkedHashMap<>();
		final SimpleDateFormat format = new SimpleDateFormat(PRINT_FORMAT);
		for (SampledValue sv: values) {
			map.put(format.format(new Date(sv.getTimestamp())), sv);
		}
		return map;
	}
	
	@Descriptor("Print data points from a timeseries")
	public Map<String, SampledValue> printValues(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Optional limit")
			@Parameter(names= { "-l", "--limit"}, absentValue = "-1")
			int limit,
			@Descriptor("Optional flag indicating whether the first or last values are to be returned, in case a limit is set.")
			@Parameter(names= { "-fb", "--from-beginning"}, absentValue = "false", presentValue="true")
			boolean fromBeginning,
			@Descriptor("A logged value resource")
			SingleValueResource valueResource
			) throws InterruptedException {
		final ReadOnlyTimeSeries schedule = getTimeseries(valueResource);
		if (schedule == null)
			return null;
		return printValues(startTime, endTime, limit, fromBeginning, schedule);
	}
	
	@Descriptor("Print data points from a timeseries")
	public Map<String, SampledValue> printValues(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Optional limit")
			@Parameter(names= { "-l", "--limit"}, absentValue = "-1")
			int limit,
			@Descriptor("Optional flag indicating whether the first or last values are to be returned, in case a limit is set.")
			@Parameter(names= { "-fb", "--from-beginning"}, absentValue = "false", presentValue="true")
			boolean fromBeginning,
			@Descriptor("Schedule resource path")
			String path
			) throws InterruptedException {
		final ReadOnlyTimeSeries r = getTimeseries(path);
		if (r == null)
			return null;
		return printValues(startTime, endTime, limit, fromBeginning, r);
	}
	
	private Object firstLastTimestamp(
			String startTime,
			String format,
			ReadOnlyTimeSeries schedule,
			boolean firstOrLast
			) throws InterruptedException {
		Long start = parseDatetimeWithExpression(startTime);
		if (start == null)
			start = firstOrLast ? Long.MIN_VALUE : Long.MAX_VALUE;
		final SampledValue first = firstOrLast ? schedule.getNextValue(start) : schedule.getPreviousValue(start);
		if (first == null)
			return null;
		final long t = first.getTimestamp();
		if (format.equalsIgnoreCase("string")) {
			final SimpleDateFormat formatter = new SimpleDateFormat(PRINT_FORMAT);
			return formatter.format(new Date(t));
		}
		return t;
	}
	
	@Descriptor("Get the first timeseries of a timeseries")
	public Object firstTimestamp(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Specify format: either 'string' (default) or 'long'")
			@Parameter(names= { "-f", "--format"}, absentValue = "string")
			String format,
			@Descriptor("Timeseries/Schedule")
			ReadOnlyTimeSeries schedule
			) throws InterruptedException {
		return firstLastTimestamp(startTime, format, schedule, true);
	}
	
	@Descriptor("Get the first timestamp of a timeseries")
	public Object firstTimestamp(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Specify format: either 'string' (default) or 'long'")
			@Parameter(names= { "-f", "--format"}, absentValue = "string")
			String format,
			@Descriptor("Schedule resource path")
			String path
			) throws InterruptedException {
		final ReadOnlyTimeSeries r = getTimeseries(path);
		if (r == null)
			return null;
		return firstTimestamp(startTime, format, r);
	}
	
	@Descriptor("Get the first timestamp of a timeseries")
	public Object firstTimestamp(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Specify format: either 'string' (default) or 'long'")
			@Parameter(names= { "-f", "--format"}, absentValue = "string")
			String format,
			@Descriptor("A logged value resource")
			SingleValueResource valueResource
			) throws InterruptedException {
		final ReadOnlyTimeSeries schedule = getTimeseries(valueResource);
		if (schedule == null)
			return null;
		return firstTimestamp(startTime, format, schedule);
	}
	
	@Descriptor("Get the last timestamp of a timeseries")
	public Object lastTimestamp(
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Specify format: either 'string' (default) or 'long'")
			@Parameter(names= { "-f", "--format"}, absentValue = "string")
			String format,
			@Descriptor("Timeseries/Schedule")
			ReadOnlyTimeSeries schedule
			) throws InterruptedException {
		return firstLastTimestamp(endTime, format, schedule, false);
	}
	
	@Descriptor("Get the last timestamp of a timeseries")
	public Object lastTimestamp(
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Specify format: either 'string' (default) or 'long'")
			@Parameter(names= { "-f", "--format"}, absentValue = "string")
			String format,
			@Descriptor("Schedule resource path")
			String path
			) throws InterruptedException {
		final ReadOnlyTimeSeries r = getTimeseries(path);
		if (r == null)
			return null;
		return lastTimestamp(endTime, format, r);
	}
	
	@Descriptor("Get the last timestamp of a timeseries")
	public Object lastTimestamp(
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Specify format: either 'string' (default) or 'long'")
			@Parameter(names= { "-f", "--format"}, absentValue = "string")
			String format,
			@Descriptor("A logged value resource")
			SingleValueResource valueResource
			) throws InterruptedException {
		final ReadOnlyTimeSeries schedule = getTimeseries(valueResource);
		if (schedule == null)
			return null;
		return lastTimestamp(endTime, format, schedule);
	}
	
	
	@Descriptor("Count the number of data points in a timeseries")
	public int size(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Timeseries/Schedule")
			ReadOnlyTimeSeries schedule
			) throws InterruptedException {
		Long start = parseDatetimeWithExpression(startTime);
		Long end = parseDatetimeWithExpression(endTime);
		if (start == null && end == null)
			return schedule.size();
		if (start == null)
			start = Long.MIN_VALUE;
		if (end == null)
			end = Long.MAX_VALUE;
		return schedule.size(start, end);
	}
	
	@Descriptor("Count the number of data points in a timeseries")
	public int size(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("A logged value resource")
			SingleValueResource valueResource
			) throws InterruptedException {
		final ReadOnlyTimeSeries schedule = getTimeseries(valueResource);
		if (schedule == null)
			return 0;
		return size(startTime, endTime, schedule);
	}
	
	@Descriptor("Count the number of data points in a timeseries")
	public int size(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Schedule resource path")
			String path
			) throws InterruptedException {
		if (path.trim().isEmpty())
			return 0;
		final ReadOnlyTimeSeries r = getTimeseries(path);
		if (r == null)
			return 0;
		return size(startTime, endTime, r);
	}
	
	@Descriptor("Add data points to a timeseries")
	public int addValues(
			@Descriptor("Activate the timeseries, in case it is an OGEMA schedule resource?")
			@Parameter(names= { "-a", "--activate"}, absentValue = "false", presentValue="true")
			boolean activate,
			@Descriptor("Value format: 'f': float (default), 'd': double, 'i': integer, 'l': long, 's': string, 'b': boolean")
			@Parameter(names= { "-f", "--format"}, absentValue = "f")
			String format,
			@Descriptor("Set the quality of the new datapoints to BAD.")
			@Parameter(names= { "-b", "--bad"}, absentValue = "false", presentValue="true")
			boolean badQuality,
			@Descriptor("Timeseries/Schedule")
			TimeSeries schedule,
			@Descriptor("The values, in")
			String values
			) throws InterruptedException {
		final List<SampledValue> valuesList = parseValues(values, format, badQuality ? Quality.BAD : Quality.GOOD);
		if (valuesList.isEmpty())
			return 0;
		final boolean success = schedule.addValues(valuesList);
		if (activate && schedule instanceof Schedule)
			((Schedule) schedule).activate(false);
		return success ? valuesList.size() : 0;
	}
	
	@Descriptor("Add data points to a timeseries")
	public int addValues(
			@Descriptor("Activate the timeseries, in case it is an OGEMA schedule resource?")
			@Parameter(names= { "-a", "--activate"}, absentValue = "false", presentValue="true")
			boolean activate,
			@Descriptor("Value format: 'f': float (default), 'd': double, 'i': integer, 'l': long, 's': string, 'b': boolean")
			@Parameter(names= { "-f", "--format"}, absentValue = "f")
			String format,
			@Descriptor("Set the quality of the new datapoints to BAD.")
			@Parameter(names= { "-b", "--bad"}, absentValue = "false", presentValue="true")
			boolean badQuality,
			@Descriptor("Path of a schedule resource")
			String schedulePath,
			@Descriptor("The values, in")
			String values
			) throws InterruptedException {
		startLatch.await(30, TimeUnit.SECONDS);
		final Resource r = appMan.getResourceAccess().getResource(schedulePath);
		if (r == null) {
			System.out.println("Resource not found: " + schedulePath);
			return 0;
		}
		if (!(r instanceof Schedule)) {
			System.out.println("Resource " + r+ " is not a schedule, cannot add datapoints");
			return 0;
		}
		if (!r.exists())
			r.create();
		return addValues(activate, format, badQuality, (Schedule) r, values);
	}
	
	private ReadOnlyTimeSeries getTimeseries(String path) throws InterruptedException {
		startLatch.await(30, TimeUnit.SECONDS);
		final Resource r = appMan.getResourceAccess().getResource(path);
		if (r == null) {
			System.out.println("Resource " + path + " not found");
			return null;
		}
		return getTimeseries(r);
	}
	
	private static ReadOnlyTimeSeries getTimeseries(Resource r) {
		if (r instanceof Schedule)
			return (Schedule) r;
		if (r instanceof FloatResource)
			return ((FloatResource) r).getHistoricalData();
		if (r instanceof IntegerResource)
			return ((IntegerResource) r).getHistoricalData();
		if (r instanceof TimeResource)
			return ((TimeResource) r).getHistoricalData();
		if (r instanceof BooleanResource)
			return ((BooleanResource) r).getHistoricalData();
		System.out.println("Cannot convert resource " + r + " into a timeseries");
		return null;
	}

	private static Long parseDatetime(String datetime) {
		if (datetime == null || datetime.isEmpty())
			return null;
		try {
			return Long.parseLong(datetime);
		} catch (NumberFormatException e) {}
		for (String format: PARSE_FORMATS) {
			try {
				return new SimpleDateFormat(format).parse(datetime).getTime();
			} catch (ParseException e) {}
		}
		return null;
	}
	
	private Long parseDatetimeWithExpression(String datetime) throws InterruptedException {
		Long t = parseDatetime(datetime);
		if (t != null)
			return t;
		datetime = datetime.toLowerCase().trim();
		if (!datetime.startsWith("now"))
			return null;
		startLatch.await(30, TimeUnit.SECONDS);
		final long now = appMan.getFrameworkTime();
		if (datetime.equals("now"))
			return now;
		return now + parseDuration(datetime.substring(3));
	}
	
	// parse input string of the form +- duration unit
	private static long parseDuration(String duration) {
		int state = 0; // 0 before sign; 1: after sign; 2: in duration; 3: after duration; 4 in unit
		boolean plusOrMinus = true;
		int durationStart = -1;
		int durationEnd = -1;
		int unitStart = -1;
		for (int idx=0; idx<duration.length(); idx++) {
			final char c = duration.charAt(idx);
			switch (state) {
			case 0:
				if (Character.isWhitespace(c))
					continue;
				if (c == '+' || c == '-') {
					state += 1;
					plusOrMinus = c == '+';
				}
				break;
			case 1:
				if (Character.isWhitespace(c))
					continue;
				state += 1;
				durationStart = idx;
				break;
			case 2:
				if (Character.isDigit(c) || c == '.')
					continue;
				state += 1;
				durationEnd =idx;
				// fall through
			case 3:
				if (Character.isWhitespace(c))
					continue;
				state += 1;
				unitStart = idx;
				break;
			case 4://?
			}
		}
		if (state < 4)
			throw new IllegalArgumentException("Could not parse duration " + duration);
		final String unit = duration.substring(unitStart);
		final long unitMultiplier = unitMultiplier(unit);
		final String durationString = duration.substring(durationStart, durationEnd);		
		final long totalDuration = durationString.indexOf('.') > 0 ? (long) (Double.parseDouble(durationString) * unitMultiplier) : 
				Long.parseLong(durationString) * unitMultiplier;
		return plusOrMinus ? totalDuration : -totalDuration;
	}
	
	private static long unitMultiplier(String unit) {
		switch(unit) {
		case "ms":
		case "millis":
		case "milliseconds":
			return 1;
		case "s":
		case "second":
		case "seconds":
			return 1000;
		case "m":
		case "min":
		case "minute":
		case "minutes":
			return 60_000;
		case "h":
		case "hour":
		case "hours":
			return 3_600_000;
		case "d":
		case "day":
		case "days":
			return 86_400_000;
		case "w":
		case "week":
		case "weeks":
			return 7 * 86_400_000;
		case "month":
		case "months":
			return 30 * 86_400_000;
		case "a":
		case "y":
		case "year":
		case "years":
			return 365 * 86_400_000;
		}
		throw new IllegalArgumentException("Unknown temporal unit " + unit);
	}
	
	private static Value parseValue(String value, String format) {
		switch(format.trim().toLowerCase()) {
		case "f":
		case "float":
			return new FloatValue(Float.parseFloat(value));
		case "d":
		case "double":
			return new DoubleValue(Double.parseDouble(value));
		case "i":
		case "int":
		case "integer":
			return new IntegerValue(Integer.parseInt(value));
		case "l":
		case "long":
			return new LongValue(Long.parseLong(value));
		case "b":
		case "bool":
		case "boolean":
			return new BooleanValue(Boolean.parseBoolean(value));
		case "s":
		case "string":
			return new StringValue(value);
		default:
			throw new IllegalArgumentException("Invalid format " + format);
		}
	}
	
	private List<SampledValue> parseValues(String values, String format, Quality quality) throws InterruptedException {
		final List<SampledValue> result = new ArrayList<>();
		for (String value: values.split(",")) {
			value = value.trim();
			if (value.isEmpty())
				continue;
			final String[] pair = value.split("=");
			if (pair.length != 2 || pair[0].trim().isEmpty() || pair[1].trim().isEmpty())
				throw new IllegalArgumentException("String " + value + " does not conform to the format <TIMESTAMP>=<VALUE>");
			final Long t = parseDatetimeWithExpression(pair[0].trim());
			if (t == null)
				throw new IllegalArgumentException("Failed to parse timestamp " + pair[0].trim());
			final Value valueObj = parseValue(pair[1].trim(), format);
			final SampledValue sv = new SampledValue(valueObj, t, quality);
			result.add(sv);
 		}
		return result;
	}
	

}
