package ca.uhn.fhir.jpa.bulk.imprt2;

import ca.uhn.fhir.batch2.api.IJobCleanerService;
import ca.uhn.fhir.batch2.api.IJobCoordinator;
import ca.uhn.fhir.batch2.model.JobInstance;
import ca.uhn.fhir.batch2.model.JobInstanceParameter;
import ca.uhn.fhir.batch2.model.JobInstanceStartRequest;
import ca.uhn.fhir.batch2.model.StatusEnum;
import ca.uhn.fhir.interceptor.api.IAnonymousInterceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.dao.r4.BaseJpaR4Test;
import ca.uhn.fhir.test.utilities.server.HttpServletExtension;
import ca.uhn.fhir.util.JsonUtil;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.StringReader;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class BulkImportR4Test extends BaseJpaR4Test {

	private static final Logger ourLog = LoggerFactory.getLogger(BulkImportR4Test.class);
	@Autowired
	private IJobCoordinator myJobCoordinator;
	@Autowired
	private IJobCleanerService myJobCleanerService;
	private final BulkImportFileServlet myBulkImportFileServlet = new BulkImportFileServlet();
	@RegisterExtension
	private final HttpServletExtension myHttpServletExtension = new HttpServletExtension()
		.withServlet(myBulkImportFileServlet);

	@AfterEach
	public void afterEach() {
		myBulkImportFileServlet.clearFiles();
	}

	@Test
	public void testRunBulkImport() {
		// Setup

		int fileCount = 100;
		addFiles(fileCount);

		JobInstanceStartRequest request = new JobInstanceStartRequest();
		request.setJobDefinitionId(BulkImport2AppCtx.JOB_BULK_IMPORT_PULL);
		for (int i = 0; i < fileCount; i++) {
			String url = myHttpServletExtension.getBaseUrl() + "/download?index=" + i;
			request.addParameter(new JobInstanceParameter(BulkImport2AppCtx.PARAM_NDJSON_URL, url));
		}

		// Execute

		String instanceId = myJobCoordinator.startInstance(request);
		assertThat(instanceId, not(blankOrNullString()));
		ourLog.info("Execution got ID: {}", instanceId);

		// Verify

		await().atMost(1, TimeUnit.MINUTES).until(() -> {
			myJobCleanerService.runCleanupPass();
			JobInstance instance = myJobCoordinator.getInstance(instanceId);
			return instance.getStatus();
		}, equalTo(StatusEnum.COMPLETED));

		runInTransaction(() -> {
			JobInstance instance = myJobCoordinator.getInstance(instanceId);
			ourLog.info("Instance details:\n{}", JsonUtil.serialize(instance, true));
			assertEquals(0, instance.getErrorCount());
			assertNotNull(instance.getCreateTime());
			assertNotNull(instance.getStartTime());
			assertNotNull(instance.getEndTime());
			assertEquals(200, instance.getCombinedRecordsProcessed());
			assertThat(instance.getCombinedRecordsProcessedPerSecond(), greaterThan(5.0));
		});
	}

	@Test
	public void testRunBulkImport_StorageFailure() {
		// Setup

		int fileCount = 3;
		addFiles(fileCount);

		JobInstanceStartRequest request = new JobInstanceStartRequest();
		request.setJobDefinitionId(BulkImport2AppCtx.JOB_BULK_IMPORT_PULL);
		for (int i = 0; i < fileCount; i++) {
			String url = myHttpServletExtension.getBaseUrl() + "/download?index=" + i;
			request.addParameter(new JobInstanceParameter(BulkImport2AppCtx.PARAM_NDJSON_URL, url));
		}

		IAnonymousInterceptor anonymousInterceptor = (thePointcut, theArgs) -> {
			throw new NullPointerException("This is an exception");
		};
		myInterceptorRegistry.registerAnonymousInterceptor(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED, anonymousInterceptor);
		try {

			// Execute

			String instanceId = myJobCoordinator.startInstance(request);
			assertThat(instanceId, not(blankOrNullString()));
			ourLog.info("Execution got ID: {}", instanceId);

			// Verify

			await().until(() -> {
				myJobCleanerService.runCleanupPass();
				JobInstance instance = myJobCoordinator.getInstance(instanceId);
				return instance.getStatus();
			}, equalTo(StatusEnum.ERRORED));

			runInTransaction(() -> {
				JobInstance instance = myJobCoordinator.getInstance(instanceId);
				ourLog.info("Instance details:\n{}", JsonUtil.serialize(instance, true));
				assertEquals(3, instance.getErrorCount());
				assertNotNull(instance.getCreateTime());
				assertNotNull(instance.getStartTime());
				assertNull(instance.getEndTime());
				assertThat(instance.getErrorMessage(), containsString("NullPointerException: This is an exception"));
			});

		} finally {

			myInterceptorRegistry.unregisterInterceptor(anonymousInterceptor);

		}
	}


	@Test
	public void testRunBulkImport_InvalidFileContents() {
		// Setup

		int fileCount = 3;
		addFiles(fileCount - 1);
		myBulkImportFileServlet.registerFile(() -> new StringReader("{\"resourceType\":\"Foo\"}"));

		JobInstanceStartRequest request = new JobInstanceStartRequest();
		request.setJobDefinitionId(BulkImport2AppCtx.JOB_BULK_IMPORT_PULL);
		for (int i = 0; i < fileCount; i++) {
			String url = myHttpServletExtension.getBaseUrl() + "/download?index=" + i;
			request.addParameter(new JobInstanceParameter(BulkImport2AppCtx.PARAM_NDJSON_URL, url));
		}

		// Execute

		String instanceId = myJobCoordinator.startInstance(request);
		assertThat(instanceId, not(blankOrNullString()));
		ourLog.info("Execution got ID: {}", instanceId);

		// Verify

		await().atMost(1, TimeUnit.MINUTES).until(() -> {
			myJobCleanerService.runCleanupPass();
			JobInstance instance = myJobCoordinator.getInstance(instanceId);
			return instance.getStatus();
		}, equalTo(StatusEnum.FAILED));

		JobInstance instance = myJobCoordinator.getInstance(instanceId);
		ourLog.info("Instance details:\n{}", JsonUtil.serialize(instance, true));
		assertEquals(1, instance.getErrorCount());
		assertEquals(StatusEnum.FAILED, instance.getStatus());
		assertNotNull(instance.getCreateTime());
		assertNotNull(instance.getStartTime());
		assertNotNull(instance.getEndTime());
		assertThat(instance.getErrorMessage(), containsString("Unknown resource name \"Foo\""));
	}

	private void addFiles(int fileCount) {
		for (int i = 0; i < fileCount; i++) {
			StringBuilder builder = new StringBuilder();

			Patient patient = new Patient();
			patient.setId("Patient/P" + i);
			patient.setActive(true);
			builder.append(myFhirCtx.newJsonParser().setPrettyPrint(false).encodeResourceToString(patient));
			builder.append("\n");

			Observation observation = new Observation();
			observation.setId("Observation/O" + i);
			observation.getSubject().setReference("Patient/P" + i);
			builder.append(myFhirCtx.newJsonParser().setPrettyPrint(false).encodeResourceToString(observation));
			builder.append("\n");
			builder.append("\n");

			myBulkImportFileServlet.registerFile(() -> new StringReader(builder.toString()));
		}
	}
}