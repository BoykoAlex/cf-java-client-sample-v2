package com.github.kdvolder.cfv2sample;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstancesRequest;
import org.cloudfoundry.client.v2.serviceinstances.ServiceInstanceResource;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.CloudFoundryOperationsBuilder;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.GetApplicationEnvironmentsRequest;
import org.cloudfoundry.operations.applications.GetApplicationRequest;
import org.cloudfoundry.operations.applications.SetEnvironmentVariableApplicationRequest;
import org.cloudfoundry.operations.applications.UnsetEnvironmentVariableApplicationRequest;
import org.cloudfoundry.operations.services.CreateServiceInstanceRequest;
import org.cloudfoundry.operations.spaces.GetSpaceRequest;
import org.cloudfoundry.spring.client.SpringCloudFoundryClient;
import org.cloudfoundry.util.PaginationUtils;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CFV2SampleMain {

	private static final String SPACE_NAME = "kdevolder";

	SpringCloudFoundryClient client = SpringCloudFoundryClient.builder()
			.username("kdevolder@gopivotal.com")
			.password(System.getProperty("cf.password"))
			.host("api.run.pivotal.io")
			.build();

	CloudFoundryOperations cfops = new CloudFoundryOperationsBuilder()
			.cloudFoundryClient(client)
			.target("FrameworksAndRuntimes", SPACE_NAME)
			.build();

	String spaceId = getSpaceId();

	public static void main(String[] args) throws Exception {
		int i = 1;
		while (true) {
			System.out.println("Iteration: "+(i++));
			long startTime = System.currentTimeMillis();
			new CFV2SampleMain().showApplications();
			long duration = System.currentTimeMillis() - startTime;
			System.out.println("Duration = "+Duration.ofMillis(duration));
			System.out.println("Threads  = "+Thread.activeCount());
			dumpThreadNames();
			System.gc(); System.gc(); System.gc(); System.gc();
			System.gc(); System.gc(); System.gc(); System.gc();
			System.gc(); System.gc(); System.gc(); System.gc();

			System.out.println("============================");
		}



//		new CFV2SampleMain().deleteLastEnvBugDemo();
//		deleteLastEnvBugDemo();
//		createService("konijn", "cloudamqp", "lemur");
//		showApplicationDetails("demo456");
//		showServices();
	}


	private static void dumpThreadNames() {
		Thread[] ts = new Thread[Thread.activeCount()];
		int count = Thread.enumerate(ts);
		for (int i = 0; i < count; i++) {
			System.out.println(ts[i].getName());
		}
	}


	void deleteLastEnvBugDemo() {
		Map<String,String> env = new HashMap<>();
		env.put("foo", "ThisisFoo");
		env.put("bar", "ThisisBar");

		String appName = "dododododo";
		setEnvVars(appName, env).get();
		System.out.println("Finished settting env vars.");

		showEnv(appName);

		for (String keyToRemove : env.keySet()) {
			System.out.println("Removing '"+keyToRemove+"'");
			unsetEnv(appName, keyToRemove);
			showEnv(appName);
		}
	}


	void unsetEnv(String appName, String keyToRemove) {
		cfops.applications()
		.unsetEnvironmentVariable(UnsetEnvironmentVariableApplicationRequest.builder()
				.name(appName)
				.variableName(keyToRemove)
				.build()
		)
		.get();
	}


	void showEnv(String appName) {
		System.out.println("=== dumping env ===");
		Map<String, Object> env = getEnv(appName).get();
		for (Entry<String, Object> e : env.entrySet()) {
			System.out.println(e.getKey()+" = "+e.getValue());
		}
		System.out.println("===================");
	}

	Mono<Map<String,Object>> getEnv(String appName) {
		return cfops.applications().getEnvironments(GetApplicationEnvironmentsRequest.builder()
				.name(appName)
				.build()
		).map((envs) -> envs.getUserProvided());
	}

	Mono<Void> setEnvVars(String appName, Map<String, String> env) {
		if (env==null) {
			return Mono.empty();
		} else {
			Mono<Void> setAll = Mono.empty();
			for (Entry<String, String> entry : env.entrySet()) {
				setAll = setAll.after(() -> {
					System.out.println("Set var starting: "+entry);
					return cfops.applications()
					.setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
							.name(appName)
							.variableName(entry.getKey())
							.variableValue(entry.getValue())
							.build()
					)
					.after(() -> {
						System.out.println("Set var complete: "+entry);
						return Mono.empty();
					});
				});
			}
			return setAll;
		}
	}

//	private static Mono<Void> setEnvVars(String appName, Map<String, String> env) {
//		//XXX CF V2: bug in CF V2: https://www.pivotaltracker.com/story/show/116725155
//		// Also this code does not unset env vars, but it probably should.
//		if (env==null) {
//			return Mono.empty();
//		} else {
//			return Flux.fromIterable(env.entrySet())
//			.flatMap((entry) -> {
//				System.out.println("Set var starting: "+entry);
//				return cfops.applications()
//				.setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
//						.name(appName)
//						.variableName(entry.getKey())
//						.variableValue(entry.getValue())
//						.build()
//				)
//				.after(() -> {
//					System.out.println("Set var complete: "+entry);
//					return Mono.empty();
//				});
//			})
//			.after();
//		}
//	}

	void createService(String name, String service, String plan) {
		System.out.println("============================");
		cfops.services().createInstance(CreateServiceInstanceRequest.builder()
				.serviceInstanceName(name)
				.serviceName(service)
				.planName(plan)
				.build()
		)
		.get();
		System.out.println("Created a Service!");
	}

	void showApplicationDetails(String name) {
		System.out.println("============================");
		ApplicationDetail app = cfops.applications()
		.get(GetApplicationRequest.builder()
			.name(name)
			.build()
		)
		.get();

		System.out.println("name = "+app.getName());
		System.out.println("requested state = "+app.getRequestedState());
		System.out.println("buildpack = " + app.getBuildpack());
	}

	String getSpaceId() {
		try {
			return cfops.spaces().get(spaceWithName(SPACE_NAME)).toCompletableFuture().get().getId();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	GetSpaceRequest spaceWithName(String spaceName) {
		return GetSpaceRequest.builder()
				.name(spaceName)
				.build();
	}

	void showApplications() {
		List<String> names = cfops
			.applications()
			.list()
			.map(ApplicationSummary::getName)
			.toList()
			.map(ImmutableList::copyOf)
			.get();
		System.out.println("Applications: "+names);
	}

	Flux<ServiceInstanceResource> requestServices(CloudFoundryClient cloudFoundryClient) {
		return PaginationUtils.requestResources((page) -> {
			ListServiceInstancesRequest request = ListServiceInstancesRequest.builder()
					.page(page)
					.spaceId(spaceId)
					.build();
			return client.serviceInstances().list(request);
		});
	}

	void showServices() {
		System.out.println("============================");
		ImmutableList<String> serviceNames = requestServices(client)
			.map((ServiceInstanceResource service) -> service.getEntity().getName())
			.toList()
			.map(ImmutableList::copyOf)
			.get();
		System.out.println("Services: "+serviceNames);
	}



}