package com.github.kdvolder.cfv2sample;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.zip.ZipFile;

import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryClient;
import org.cloudfoundry.client.lib.archive.ZipApplicationArchive;
import org.cloudfoundry.client.v2.applications.CreateApplicationRequest;
import org.cloudfoundry.client.v2.applications.UpdateApplicationRequest;
import org.cloudfoundry.doppler.DopplerClient;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.DefaultCloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.DeleteApplicationRequest;
import org.cloudfoundry.operations.applications.GetApplicationRequest;
import org.cloudfoundry.operations.applications.RestartApplicationRequest;
import org.cloudfoundry.operations.applications.StartApplicationRequest;
import org.cloudfoundry.operations.routes.Level;
import org.cloudfoundry.operations.routes.ListRoutesRequest;
import org.cloudfoundry.operations.routes.Route;
import org.cloudfoundry.operations.spaces.GetSpaceRequest;
import org.cloudfoundry.operations.spaces.SpaceDetail;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.doppler.ReactorDopplerClient;
import org.cloudfoundry.reactor.tokenprovider.AbstractUaaTokenProvider;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.cloudfoundry.reactor.tokenprovider.RefreshTokenGrantTokenProvider;
import org.cloudfoundry.reactor.uaa.ReactorUaaClient;
import org.cloudfoundry.uaa.UaaClient;

import reactor.core.publisher.Mono;

public class CFV2SampleMain  {
	
	private static final String tokenPath = ".cf_java_client.json";

	private static final String API_HOST = "api.run.pivotal.io";
	private static final String ORG_NAME = "FrameworksAndRuntimes";
	private static final String SPACE_NAME = "kdevolder";
	private static final boolean SKIP_SSL = false;
	private static final String USER = "kdevolder@gopivotal.com";
	
	private static final String APP_JAR = "/Users/aboyko/documents/cloud-ide/demo-2/target/demo-2-0.0.1-SNAPSHOT.jar";
	
	ConnectionContext connection = DefaultConnectionContext.builder()
			.apiHost(API_HOST)
			.skipSslValidation(SKIP_SSL)
			.build();
	
	AbstractUaaTokenProvider tokenProvider = createTokenProvider();
	
	ReactorCloudFoundryClient client = ReactorCloudFoundryClient.builder()
			.connectionContext(connection)
			.tokenProvider(tokenProvider)
			.build();
	
	UaaClient uaaClient = ReactorUaaClient.builder()
			.connectionContext(connection)
			.tokenProvider(tokenProvider)
			.build();

	DopplerClient doppler = ReactorDopplerClient.builder()
			.connectionContext(connection)
			.tokenProvider(tokenProvider)
			.build();

	CloudFoundryOperations cfops = DefaultCloudFoundryOperations.builder()
			.cloudFoundryClient(client)
			.dopplerClient(doppler)
			.uaaClient(uaaClient)
			.organization(ORG_NAME)
			.space(SPACE_NAME)
			.build();
	
	CloudFoundryClient v1 = new CloudFoundryClient(new CloudCredentials(USER, System.getProperty("cf.password")), createCfApiUrl(), ORG_NAME, SPACE_NAME, false);

	private void getSshCode() {
		System.out.println("ssh code = '"+cfops.advanced().sshCode().block()+"'");
	}

	protected AbstractUaaTokenProvider createTokenProvider() {
		TokenFile tokenFile = TokenFile.read(new File(tokenPath));
		if (tokenFile!=null) {
			System.out.println("Using stored REFRESH token for auth");
			return RefreshTokenGrantTokenProvider.builder()
					.token(tokenFile.getRefreshToken())
					.build();
		} else {
			System.out.println("Using PASSWORD token for auth");
			return PasswordGrantTokenProvider.builder()
					.username(USER)
					.password(System.getProperty("cf.password"))
					.build();
		}
	}

	public static void main(String[] args) throws Exception {
		System.out.println("Starting...");
		CFV2SampleMain cfv2Sample = new CFV2SampleMain();
		
//		cfv2Sample.showApplicationsWithDetails();
		
//		System.out.println("First push");
//		cfv2Sample.pushApp("aboyko-demo-2", getAppArchive());
//		for (int i = 0; i < 10; i++) {
//			System.out.println("Re-pushing " + (i+1) + "...");
//			cfv2Sample.pushApp("aboyko-demo-2", getAppArchive());
//			Thread.sleep(1000);
//		}
		
		while(true) {
			cfv2Sample.getRoutesFromSpace().forEach(System.out::println);
			Thread.sleep(500);
		}
	}
	
	private void saveRefreshToken() {
		try {
			String refreshToken = tokenProvider.getRefreshToken();
			TokenFile tokenFile = new TokenFile();
			tokenFile.setRefreshToken(refreshToken);
			tokenFile.write(new File(tokenPath));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void showApplicationsWithDetails() {
		cfops.applications()
		.list()
		.flatMap((appSummary) -> {
			return cfops.applications().get(GetApplicationRequest.builder()
				.name(appSummary.getName())
				.build()
			)
			.otherwise((error) -> {
				System.err.println("Error gettting details for app: "+appSummary.getName());
				error.printStackTrace(System.err);
				return Mono.empty();
			});
		})
		.doOnComplete(this::saveRefreshToken)
		.doOnTerminate(() -> System.out.println("====done===="))
		.subscribe(System.out::println);
//		while (true) {
//			try {
//				Thread.sleep(2000);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
	}
	
	private Mono<ApplicationDetail> getApplication(String name) {
		return cfops.applications().get(GetApplicationRequest.builder().name(name).build());
	}
	
	private void deleteApplication(String name) {
		cfops.applications().delete(DeleteApplicationRequest.builder().name(name).build()).block();
	}
	
	public Mono<Void> ifApplicationExists(String appName, Function<ApplicationDetail, Mono<Void>> then, Mono<Void> els) throws Exception {
		return getApplication(appName)
		.map((app) -> Optional.of(app))
		.otherwiseIfEmpty(Mono.just(Optional.<ApplicationDetail>empty()))
		.otherwise((error) -> Mono.just(Optional.<ApplicationDetail>empty()))
		.then((Optional<ApplicationDetail> app) -> {
			if (app.isPresent()) {
				return then.apply(app.get());
			} else {
				return els;
			}
		});
	}
	
	private Mono<UUID> updateApp(UUID appId) {
			UpdateApplicationRequest req = UpdateApplicationRequest.builder()
				.applicationId(appId.toString())
				.build();
			return client.applicationsV2().update(req)
			.then(Mono.just(appId));
	}

	private Mono<Void> pushExisting(ApplicationDetail app, ZipFile file) {
		String appName = app.getName();
		UUID appId = UUID.fromString(app.getId());
		return updateApp(appId)
		.then(getApplication(appName))
		.then(Mono.fromCallable(() -> {
			v1.uploadApplication(appName, new ZipApplicationArchive(file));
			return "who cares";
		}))
		.then(restartApp(appName)
		);
	}


	private void pushApp(String appName, ZipFile file) {
		try {
			ifApplicationExists(appName,
					((app) -> pushExisting(app, file)),
					firstPush(appName, file)
				).block();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static URL createCfApiUrl() {
		try {
			return new URL("http://" + API_HOST);
		} catch (MalformedURLException e) {
			return null;
		}
	}
	
	private Mono<Void> restartApp(String appName) {
		return cfops.applications().restart(RestartApplicationRequest.builder().name(appName).build());
	}
	
	private Mono<Void> firstPush(String appName, ZipFile file) {
		return createApp(appName)
		.then(getApplication(appName))
		.then(Mono.fromCallable(() -> {
			v1.uploadApplication(appName, new ZipApplicationArchive(file));
			return "who cares";
		}))
		.then(startApp(appName)
		)
		.then();
	}
	
	private Mono<UUID> createApp(String name) {
		return cfops.spaces().get(GetSpaceRequest.builder()
				.name(SPACE_NAME)
				.build())
				.map(SpaceDetail::getId)
		.then(spaceId -> {
			CreateApplicationRequest req = CreateApplicationRequest.builder()
					.spaceId(spaceId)
					.name(name)
					.build();
			return client.applicationsV2().create(req);
		})
		.map(response -> UUID.fromString(response.getMetadata().getId()));
	}
	
	private Mono<Void> startApp(String appName) {
		return cfops.applications()
			.start(StartApplicationRequest.builder()
				.name(appName)
				.build()
			);
	}

	private static ZipFile getAppArchive() {
		try {
			return new ZipFile(APP_JAR);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private List<Route> getRoutesFromSpace() {
		return cfops.routes().list(ListRoutesRequest.builder()
				.level(Level.SPACE)
				.build()
		)
		.collectList()
		.block();
	}

}
