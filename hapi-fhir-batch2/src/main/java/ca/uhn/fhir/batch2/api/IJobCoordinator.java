package ca.uhn.fhir.batch2.api;

/*-
 * #%L
 * HAPI FHIR JPA Server - Batch2 Task Processor
 * %%
 * Copyright (C) 2014 - 2022 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import ca.uhn.fhir.batch2.model.JobInstance;
import ca.uhn.fhir.batch2.model.JobInstanceStartRequest;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

public interface IJobCoordinator {

	/**
	 * Starts a new job instance
	 *
	 * @param theStartRequest The request, containing the job type and parameters
	 * @return Returns a unique ID for this job execution
	 */
	String startInstance(JobInstanceStartRequest theStartRequest);

	/**
	 * Fetch details about a job instance
	 *
	 * @param theInstanceId The instance ID
	 * @return Returns the current instance details
	 * @throws ResourceNotFoundException If the instance ID can not be found
	 */
	JobInstance getInstance(String theInstanceId) throws ResourceNotFoundException;


	void cancelInstance(String theInstanceId) throws ResourceNotFoundException;


}
