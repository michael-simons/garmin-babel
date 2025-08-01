/*
 * Copyright 2022-2024 the original author or authors.
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
/**
 * @author Michael J. Simons
 */
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module garmin.babel {
	requires com.fasterxml.jackson.databind;
	requires info.picocli;
	requires java.measure;
	requires java.net.http;
	requires tech.units.indriya;
	requires org.apache.commons.csv;
	requires dev.failsafe.core;

	opens ac.simons.garmin to info.picocli;
}