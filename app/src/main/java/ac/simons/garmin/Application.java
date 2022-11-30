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
import static tech.units.indriya.unit.Units.METRE;
import static tech.units.indriya.unit.Units.METRE_PER_SECOND;

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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Length;
import javax.measure.quantity.Mass;
import javax.measure.quantity.Speed;

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
 * @since 1.0.0
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "FieldMayBeFinal"})
@Command(
	name = "garmin-babel",
	mixinStandardHelpOptions = true,
	description = "Massages a Garmin account archive into CSV files, optionally downloading activity files",
	sortOptions = false,
	versionProvider = ManifestVersionProvider.class,
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

	@Option(names = {"--unit-distance"}, description = "The unit to use when writing distances; valid values are: ${COMPLETION-CANDIDATES} and the default is ${DEFAULT-VALUE}")
	private NamedUnit unitDistance = NamedUnit.KILOMETRE;

	@Option(names = {"--unit-speed"}, description = "The unit to use when writing speed; valid values are: ${COMPLETION-CANDIDATES} and the default is ${DEFAULT-VALUE}")
	private NamedUnit unitSpeed = NamedUnit.KPH;

	@Option(names = {"--speed-to-pace"}, description = "Output pace instead of speed, defaults to ${DEFAULT-VALUE}")
	private boolean speedToPace = false;

	@Option(names = {"--unit-elevation-gain"}, description = "The unit to use when writing elevation; valid values are: ${COMPLETION-CANDIDATES} and the default is ${DEFAULT-VALUE}")
	private NamedUnit unitElevationGain = NamedUnit.METRE;

	@Option(names = {"--unit-weight"}, description = "The unit to use when writing weights; valid values are: ${COMPLETION-CANDIDATES} and the default is ${DEFAULT-VALUE}")
	private NamedUnit unitWeight = NamedUnit.GRAM;

	@Option(names = {"--unit-duration"}, description = "The unit to use when writing durations; valid values are: ${COMPLETION-CANDIDATES} and the default is ${DEFAULT-VALUE}")
	private ChronoUnit unitDuration = ChronoUnit.SECONDS;

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
	void dumpWeights(@Parameters(index = "0", arity = "0..1", description = "Optional target file, will be overwritten") Optional<Path> target) throws IOException, InvocationTargetException, IllegalAccessException {

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

				var value = Quantities.getQuantity(((BigDecimal) weightInfo.get(KEY_WEIGHT)), GRAM);
				var weightMeasurement = new WeightMeasurement(measuredOn, value);
				printRecord(csvPrinter, weightMeasurement);
			}
		}
	}

	@Command(name = "dump-activities")
	void dumpActivities(
		@Option(names = {"-u", "--user-name"}, required = true, description = "User name inside the archive")
		String userName,
		@Option(names = {"--sport-type"}, split = ",", description = "Included sport types, repeat for multiple types")
		Set<String> includedSportTypes,
		@Option(names = {"--activity-type"}, split = ",", description = "Included activity types, repeat for multiple types")
		Set<String> includedActivityTypes,
		@Option(names = "--min-distance", description = "Minimum distance to be included, same unit as for the whole app assumed")
		Double minDistance,
		@Option(names = "--min-elevation-gain", description = "Minimum elevation gain to be included, same unit as for the whole app assumed")
		Double minElevationGain,
		@Parameters(index = "0", arity = "0..1", description = "Optional target file, will be overwritten")
		Optional<Path> target
	) throws IOException, IllegalAccessException, InvocationTargetException {

		var baseDir = assertArchive();
		var fitnessDir = baseDir.resolve(Path.of("DI_CONNECT/DI-Connect-Fitness"));
		if (!Files.isDirectory(fitnessDir)) {
			throw new IllegalStateException("'DI_CONNECT/DI-Connect-Fitness' not found.");
		}

		// Select filenames
		var fileNamePattern = Pattern.compile(Pattern.quote(userName) + "_\\d+_summarizedActivities.json");
		var summarizedActivities = Files.list(fitnessDir)
			.filter(path -> fileNamePattern.matcher(path.getFileName().toString()).matches())
			.sorted()
			.toList();

		if (summarizedActivities.isEmpty()) {
			return;
		}

		// Load gear
		var gearAndActivities = getGearAndActivities(fitnessDir, userName);

		Set<String> sportTypes = includedSportTypes == null ? Set.of() : includedSportTypes.stream().map(s -> s.toLowerCase(Locale.getDefault())).collect(Collectors.toSet());
		Set<String> activityTypes = includedActivityTypes == null ? Set.of() : includedActivityTypes.stream().map(s -> s.toLowerCase(Locale.getDefault())).collect(Collectors.toSet());

		Predicate<Activity> includeByDate = a -> include(a.startedOn());
		Predicate<Activity> includeBySportType = sportTypes.isEmpty() ? null : a -> a.sportType() != null && sportTypes.contains(a.sportType().trim().toLowerCase(Locale.getDefault()));
		Predicate<Activity> includeByActivityType = activityTypes.isEmpty() ? null : a -> a.activityType() != null && activityTypes.contains(a.activityType().trim().toLowerCase(Locale.getDefault()));

		final Predicate<Activity> includeByType;
		if (includeBySportType != null && includeByActivityType != null) {
			includeByType = includeBySportType.or(includeByActivityType);
		} else if (includeBySportType != null) {
			includeByType = includeBySportType;
		} else {
			includeByType = Objects.requireNonNullElseGet(includeByActivityType, () -> a -> true);
		}

		Predicate<Activity> includeByDistance = a -> true;
		if (minDistance != null) {
			var minDistanceQuantity = Quantities.getQuantity(minDistance, unitDistance.getUnit(Length.class));
			includeByDistance = a -> a.distance() != null && a.distance().isGreaterThanOrEqualTo(minDistanceQuantity);
		}

		Predicate<Activity> includeByElevationGain = a -> true;
		if (minElevationGain != null) {
			var minElevationGainQuantity = Quantities.getQuantity(minElevationGain, unitElevationGain.getUnit(Length.class));
			includeByElevationGain = a -> a.elevationGain() != null && a.elevationGain().isGreaterThanOrEqualTo(minElevationGainQuantity);
		}

		var include = includeByDate
			.and(includeByType)
			.and(includeByDistance)
			.and(includeByElevationGain);

		try (var out = AppendableHolder.of(target)) {
			var csvPrinter = new CSVPrinter(out.value, csvFormat.getFormat().builder().setHeader(getHeader(Activity.class)).build());
			for (var path : summarizedActivities) {
				try (
					var in = new BufferedInputStream(Files.newInputStream(path));
					var parser = jsonFactory.createParser(in)
				) {
					if (parser.nextToken() != JsonToken.START_ARRAY) {
						throw new IllegalStateException("Expected content to be an array");
					}
					if (parser.nextToken() != JsonToken.START_OBJECT || !"summarizedActivitiesExport".equals(parser.nextFieldName()) || parser.nextToken() != JsonToken.START_ARRAY) {
						throw new IllegalStateException("Expected content to be the start of `summarizedActivitiesExport`");
					}

					while (parser.nextToken() != JsonToken.END_ARRAY) {
						var source = mapper.readValue(parser, MAP_OF_OBJECTS);
						var activity = Activity.of(source);

						activity.gear().addAll(gearAndActivities.getGearOf(activity.garminId()));
						if (!include.test(activity)) {
							continue;
						}
						printRecord(csvPrinter, activity);
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Command(name = "dump-gear")
	void dumpGear(
		@Option(names = {"-u", "--user-name"}, required = true, description = "User name inside the archive")
		String userName,
		@Parameters(index = "0", arity = "0..1", description = "Optional target file, will be overwritten") Optional<Path> target
	) throws IOException {

		var baseDir = assertArchive();
		var fitnessDir = baseDir.resolve(Path.of("DI_CONNECT/DI-Connect-Fitness"));
		if (!Files.isDirectory(fitnessDir)) {
			throw new IllegalStateException("'DI_CONNECT/DI-Connect-Fitness' not found.");
		}

		var gearAndActivities = getGearAndActivities(fitnessDir, userName);

		try (
			var out = AppendableHolder.of(target);
		) {
			var csvPrinter = new CSVPrinter(out.value, csvFormat.getFormat().builder().setHeader(getHeader(Gear.class)).build());
			gearAndActivities.gear().entrySet().stream().map(e -> new Gear(e.getKey(), e.getValue())).forEach(item -> {
				try {
					printRecord(csvPrinter, item);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	record GearAndActivities(Map<Long, String> gear, Map<Long, List<String>> activityMapping) {

		Collection<String> getGearOf(long garminId) {
			return activityMapping.getOrDefault(garminId, List.of());
		}
	}

	@SuppressWarnings("unchecked")
	private GearAndActivities getGearAndActivities(Path fitnessDir, String userName) throws IOException {

		var gearFile = fitnessDir.resolve(Path.of(userName + "_gear.json"));
		if (Files.isRegularFile(gearFile)) {
			try (var in = new BufferedInputStream(Files.newInputStream(gearFile))) {
				var content = mapper.readValue(in, LIST_MAP_OF_OBJECTS);
				Map<String, Object> dtos;
				if (!content.isEmpty() && (dtos = content.get(0)).containsKey("gearDTOS") && dtos.containsKey("gearActivityDTOs")) {
					var activityMapping = new HashMap<Long, List<String>>();
					var gearDTOS = (List<Map<String, Object>>) dtos.get("gearDTOS");
					var activityDTOs = (Map<String, List<Map<String, Object>>>) dtos.get("gearActivityDTOs");
					var gear = gearDTOS.stream()
						.map(g -> new Gear(((Number) g.get("gearPk")).longValue(), ((String) g.get("customMakeModel")).trim()))
						.collect(Collectors.toMap(Gear::garminId, Gear::makeAndModel));

					activityDTOs.values().stream().flatMap(Collection::stream)
						.forEach(activityDTO -> activityMapping.computeIfAbsent(((Number) activityDTO.get("activityId")).longValue(), key -> new ArrayList<>()).add(gear.get(((Number) activityDTO.get("gearPk")).longValue())));
					return new GearAndActivities(gear, Collections.unmodifiableMap(activityMapping));
				}
			}

		}

		return new GearAndActivities(Map.of(), Map.of());
	}

	private boolean include(Instant instant) {
		if (this.startDate == null && this.endDate == null) {
			return true;
		}

		var localDate = LocalDate.ofInstant(instant, ZoneId.systemDefault());
		if (this.startDate != null && localDate.isBefore(startDate)) {
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
					value = quantity.asType(Mass.class).to(unitWeight.getUnit(Mass.class)).getValue();
				} else if (unit.equals(METRE)) {
					value = quantity.asType(Length.class).to((component.getName().equals("elevationGain") ? unitElevationGain : unitDistance).getUnit(Length.class)).getValue();
				} else if (unit.equals(METRE_PER_SECOND) && speedToPace) {
					var hlp = quantity.inverse().asType(AdditionalUnits.Pace.class).to(AdditionalUnits.MINUTES_PER_KM).getValue().doubleValue();
					var minutes = Math.round(Math.floor(hlp));
					var seconds = Math.round(60 * (hlp % minutes));
					printer.print(String.format("%d:%02d", minutes, seconds));
					continue;
				} else if (unit.equals(METRE_PER_SECOND)) {
					value = quantity.asType(Speed.class).to(unitSpeed.getUnit(Speed.class)).getValue();
				} else {
					value = quantity.getValue();
				}
				printer.print(valueFormat.format(value));
			} else if (value instanceof Instant instant) {
				printer.print(instant.truncatedTo(ChronoUnit.SECONDS).toString());
			} else if (value instanceof Duration duration) {
				printer.print(formatDuration(duration));
			} else if (value instanceof Collection<?> collection) {
				printer.print(collection.stream().map(Object::toString).collect(Collectors.joining(",")));
			} else {
				printer.print(value);
			}
		}
		printer.println();
	}

	Long formatDuration(Duration duration) {
		return switch (unitDuration) {
			case SECONDS -> duration.toSeconds();
			case MINUTES -> duration.toMinutes();
			case HOURS -> duration.toHours();
			case DAYS -> duration.toDays();
			default -> duration.get(unitDuration);
		};
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

	private static final TypeReference<List<Map<String, Object>>> LIST_MAP_OF_OBJECTS = new TypeReference<>() {
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
}
