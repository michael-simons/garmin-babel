/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ac.simons.garmin;

import static tech.units.indriya.unit.Units.GRAM;
import static tech.units.indriya.unit.Units.KILOGRAM;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Mass;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.AutoComplete.GenerateCompletion;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import tech.units.indriya.quantity.Quantities;

/**
 * Program for massaging Garmin GDPR archive into something usable.
 *
 * @author Michael J. Simons
 * @since 1.0
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "FieldMayBeFinal"})
@Command(
	name = "garmin-babel",
	mixinStandardHelpOptions = true,
	description = "Massages a Garmin account archive into CSV files, optionally downloading activity files",
	sortOptions = false,
	subcommands = {
		GenerateCompletion.class,
		HelpCommand.class
	}
)
public final class Application implements Runnable {

	@Option(names = "--start-date", description = "A lower, inclusive bound for entries to be included")
	private LocalDate startDate = null;

	@Option(names = "--end-date", description = "An upper, exclusive bound for entries to be included")
	private LocalDate endDate = null;

	@Option(names = {"--unit-weight"}, description = "The unit to use when writing weights; valid values are: ${COMPLETION-CANDIDATES} and the default is ${DEFAULT-VALUE}")
	private NamedUnit weight = NamedUnit.GRAM;

	@Option(names = {"--csv-format"}, description = "The CSV format to use; valid values are: ${COMPLETION-CANDIDATES} and the default is ${DEFAULT-VALUE}")
	private CSVFormat.Predefined csvFormat = CSVFormat.Predefined.Default;

	@Parameters(index = "0", description = "Directory containing the extracted contents of the Garmin ZIP archive")
	private Path archive;

	@Spec
	private CommandSpec commandSpec;

	private final NumberFormat valueFormat;
	private final ObjectMapper mapper;
	private final JsonFactory jsonFactory;

	private Application() {

		this.valueFormat = DecimalFormat.getNumberInstance(Locale.forLanguageTag("en-US"));
		this.valueFormat.setGroupingUsed(false);
		this.valueFormat.setMaximumFractionDigits(3);
		this.mapper = new ObjectMapper();
		mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
		this.jsonFactory = mapper.getFactory();
	}

	/**
	 * Just print the error message, not the whole stack
	 */
	static class PrintExceptionMessageHandler implements IExecutionExceptionHandler {
		public int handleExecutionException(Exception ex, CommandLine cmd, CommandLine.ParseResult parseResult) {
			cmd.getErr().println(cmd.getColorScheme().errorText(ex.getMessage()));
			return cmd.getExitCodeExceptionMapper() != null
				? cmd.getExitCodeExceptionMapper().getExitCode(ex)
				: cmd.getCommandSpec().exitCodeOnExecutionException();
		}
	}

	/**
	 * Starts of PicoCLI and runs the application
	 *
	 * @param args all the arguments!
	 */
	public static void main(String... args) {

		var commandLine = new CommandLine(new Application())
			.setCaseInsensitiveEnumValuesAllowed(true)
			.setExecutionExceptionHandler(new PrintExceptionMessageHandler());

		var generateCompletionCmd = commandLine.getSubcommands().get("generate-completion");
		generateCompletionCmd.getCommandSpec().usageMessage().hidden(true);

		int exitCode = commandLine.execute(args);
		System.exit(exitCode);
	}

	@Override
	public void run() {
		throw new ParameterException(commandSpec.commandLine(), "Missing required subcommand");
	}

	@SuppressWarnings("unchecked")
	@Command(name = "dump-weights")
	void dumpWeights(@Parameters(index = "0", arity = "0..1") Optional<Path> target) throws IOException, InvocationTargetException, IllegalAccessException {

		var baseDir = assertArchive();
		var userBiometrics = baseDir.resolve(Path.of("DI_CONNECT/DI-Connect-User/user_biometrics.json"));
		if (!Files.isRegularFile(userBiometrics)) {
			throw new IllegalStateException("'user_biometrics.json' not found.");
		}

		var zoneDateTimeParser = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());
		var instantParser = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("GMT"));

		try (
			var in = new BufferedInputStream(Files.newInputStream(userBiometrics));
			var out = AppendableHolder.of(target);
			var parser = jsonFactory.createParser(in)
		) {
			if (parser.nextToken() != JsonToken.START_ARRAY) {
				throw new IllegalStateException("Expected content to be an array");
			}

			var csvPrinter = new CSVPrinter(out.value,
				csvFormat.getFormat().builder().setHeader(getHeader(WeightMeasurement.class)).build());
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				var source = mapper.readValue(parser, MAP_OF_OBJECTS);

				if (!source.containsKey(KEY_WEIGHT)) {
					continue;
				}

				var weightInfo = (Map<String, Object>) source.get(KEY_WEIGHT);
				if (!weightInfo.containsKey(KEY_WEIGHT)) {
					continue;
				}

				Instant measuredOn;
				if (weightInfo.containsKey("timestampGMT")) {
					measuredOn = instantParser.parse((String) weightInfo.get("timestampGMT"), Instant::from);
				} else {
					measuredOn = zoneDateTimeParser.parse((String) ((Map<String, Object>) source.get("metaData")).get("calendarDate"), Instant::from);
				}

				if (!include(measuredOn)) {
					continue;
				}

				Quantity<Mass> value = Quantities.getQuantity(((BigDecimal) weightInfo.get(KEY_WEIGHT)), GRAM);
				var weightMeasurement = new WeightMeasurement(measuredOn, value);
				printRecord(csvPrinter, weightMeasurement);
			}
		}
	}


	@Command(name = "dump-activities")
	void dumpActivities(
		@Option(names = {"-u", "--user-name"}, required = true, description = "User name inside the archive")
		String userName
	) {

	}

	private boolean include(Instant instant) {
		if (this.startDate == null && this.endDate == null) {
			return true;
		}

		var localDate = LocalDate.ofInstant(instant, ZoneId.systemDefault());
		if (this.startDate != null && (localDate.isBefore(startDate) || localDate.equals(startDate))) {
			return false;
		}

		return this.endDate == null || localDate.isBefore(endDate);
	}

	private <T extends Record> String[] getHeader(Class<T> type) {
		var components = type.getRecordComponents();
		return Arrays.stream(components).map(c -> toSnakeCase(c.getName())).toArray(String[]::new);
	}

	private <T extends Record> void printRecord(CSVPrinter printer, T record) throws InvocationTargetException, IllegalAccessException, IOException {
		var components = record.getClass().getRecordComponents();
		for (var component : components) {
			var value = component.getAccessor().invoke(record);
			if (value instanceof Quantity<?> quantity) {
				Unit<?> unit = quantity.getUnit().getSystemUnit();
				if (unit.equals(KILOGRAM)) {
					value = quantity.asType(Mass.class).to(weight.getUnit(Mass.class)).getValue();
				} else {
					value = quantity.getValue();
				}
				printer.print(valueFormat.format(value));
			} else if (value instanceof Instant instant) {
				printer.print(instant.truncatedTo(ChronoUnit.SECONDS).toString());
			} else {
				printer.print(value);
			}
		}
		printer.println();
	}

	private Path assertArchive() {
		if (!Files.isDirectory(this.archive)) {
			throw new IllegalArgumentException(String.format("'%s' is not a valid directory.", this.archive));
		}
		return this.archive;
	}

	private static String toSnakeCase(String name) {

		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException();
		}

		StringBuilder sb = new StringBuilder();

		int codePoint;
		int previousIndex = 0;
		int i = 0;
		while (i < name.length()) {
			codePoint = name.codePointAt(i);
			if (Character.isLowerCase(codePoint)) {
				if (i > 0 && !Character.isLetter(name.codePointAt(previousIndex))) {
					sb.append("_");
				}
			} else if (sb.length() > 0) {
				sb.append("_");
			}
			sb.append(Character.toChars(Character.toLowerCase(codePoint)));
			previousIndex = i;
			i += Character.charCount(codePoint);
		}
		return sb.toString();
	}

	private static final TypeReference<Map<String, Object>> MAP_OF_OBJECTS = new TypeReference<>() {
	};

	private static final String KEY_WEIGHT = "weight";

	private static class AppendableHolder implements Closeable {

		private final Appendable value;

		private final boolean close;

		static AppendableHolder of(Optional<Path> target) throws IOException {
			if (target.isEmpty()) {
				return new AppendableHolder(System.out, false);
			}
			var bufferedWriter = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(target.get())));
			return new AppendableHolder(bufferedWriter, true);
		}

		private AppendableHolder(Appendable value, boolean close) {
			this.value = value;
			this.close = close;
		}

		@Override
		public void close() throws IOException {
			if (!close) {
				return;
			}
			if (this.value instanceof Closeable closeable) {
				closeable.close();
			}
		}
	}


	public static void main2(String... a) throws IOException {

		/*
		try (
			var in = Files.newInputStream(Path.of(a[0]));
			var parser = jsonFactory.createParser(in)
		) {
			if (parser.nextToken() != JsonToken.START_ARRAY) {
				throw new IllegalStateException("Expected content to be an array");
			}
			if (parser.nextToken() != JsonToken.START_OBJECT || !"summarizedActivitiesExport".equals(parser.nextFieldName()) || parser.nextToken() != JsonToken.START_ARRAY) {
				throw new IllegalStateException("Expected content to be the start of `summarizedActivitiesExport`");
			}

			List<Activity> activities = new ArrayList<>();
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				var source = mapper.readValue(parser, MAP_OF_OBJECTS);
				var activity = Activity.of(source);
				activities.add(activity);
			}

			// Fasted run
			Optional<ComparableQuantity<AdditionalUnits.Pace>> fastestRun = activities.stream()
				.filter(activity -> "running".equals(activity.activityType()) && activity.avgSpeed() != null)
				.map(activity -> (ComparableQuantity<AdditionalUnits.Pace>) activity.avgSpeed().inverse().asType(AdditionalUnits.Pace.class).to(AdditionalUnits.MINUTES_PER_KM))
				.min(Comparable::compareTo);

			// Total distance
			var totalDistance = activities.stream()
				.filter(ac -> ac.distance() != null)
				.map(Activity::distance)
				.reduce(Quantities.getQuantity(0, CENTI(METRE)), Quantity::add).to(KILO(METRE));
		}*/
	}
}
