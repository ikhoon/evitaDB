/*
 *
 *                         _ _        ____  ____
 *               _____   _(_) |_ __ _|  _ \| __ )
 *              / _ \ \ / / | __/ _` | | | |  _ \
 *             |  __/\ V /| | || (_| | |_| | |_) |
 *              \___| \_/ |_|\__\__,_|____/|____/
 *
 *   Copyright (c) 2023
 *
 *   Licensed under the Business Source License, Version 1.1 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://github.com/FgForrest/evitaDB/blob/master/LICENSE
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.evitadb.api.requestResponse.data;

/**
 * This interface is meant only for internal development purposes.
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2023
 */
public interface DevelopmentConstants {

	/**
	 * This constant represents name of the system property that signalizes that evitaDB test suite is running and that
	 * certain parts of the codebase should behave differently. For example the maps needs to be sorted first in order
	 * to make the test results repeatable.
	 */
	String TEST_RUN = "__test_run__";

	/**
	 * Method returns true if the test suite is currently in progress.
	 * @return true if the test suite is currently in progress
	 */
	static boolean isTestRun() {
		return System.getProperty(TEST_RUN) != null;
	}

}
