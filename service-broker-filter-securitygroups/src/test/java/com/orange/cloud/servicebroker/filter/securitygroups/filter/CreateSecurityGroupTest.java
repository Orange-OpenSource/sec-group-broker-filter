/*
 * <!--
 *
 *     Copyright (C) 2015 Orange
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 * -->
 */

package com.orange.cloud.servicebroker.filter.securitygroups.filter;

import com.orange.cloud.servicebroker.filter.securitygroups.domain.*;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.ClientV2Exception;
import org.cloudfoundry.client.v2.applications.ApplicationEntity;
import org.cloudfoundry.client.v2.applications.ApplicationsV2;
import org.cloudfoundry.client.v2.applications.GetApplicationRequest;
import org.cloudfoundry.client.v2.applications.GetApplicationResponse;
import org.cloudfoundry.client.v2.securitygroups.*;
import org.cloudfoundry.client.v2.servicebrokers.GetServiceBrokerRequest;
import org.cloudfoundry.client.v2.servicebrokers.GetServiceBrokerResponse;
import org.cloudfoundry.client.v2.servicebrokers.ServiceBrokerEntity;
import org.cloudfoundry.client.v2.servicebrokers.ServiceBrokers;
import org.cloudfoundry.client.v2.serviceinstances.GetServiceInstanceRequest;
import org.cloudfoundry.client.v2.serviceinstances.GetServiceInstanceResponse;
import org.cloudfoundry.client.v2.serviceinstances.ServiceInstanceEntity;
import org.cloudfoundry.client.v2.serviceinstances.ServiceInstances;
import org.cloudfoundry.client.v2.serviceplans.GetServicePlanRequest;
import org.cloudfoundry.client.v2.serviceplans.GetServicePlanResponse;
import org.cloudfoundry.client.v2.serviceplans.ServicePlanEntity;
import org.cloudfoundry.client.v2.serviceplans.ServicePlans;
import org.cloudfoundry.client.v2.services.GetServiceRequest;
import org.cloudfoundry.client.v2.services.GetServiceResponse;
import org.cloudfoundry.client.v2.services.ServiceEntity;
import org.cloudfoundry.client.v2.services.Services;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.cloud.servicebroker.model.CreateServiceInstanceAppBindingResponse;
import org.springframework.cloud.servicebroker.model.CreateServiceInstanceBindingRequest;
import org.springframework.cloud.servicebroker.model.ServiceBindingResource;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class CreateSecurityGroupTest {

    public static final String RULE_DESCRIPTION = "generated by sec group filter broker for service binding<test-securitygroup-name> to allow access to service instance<service-instance-name> created from service broker<service-broker-name>.";
    static final String TEST_URI = "mysql://127.0.0.1:3306/mydb?user=2106password=Uq3YCioVsO3Dbcp4";
    static final String NO_HOST_URI = "mysql:///mydb?user=2106password=Uq3YCioVsO3Dbcp4";
    static final String NO_PORT_URI = "mysql://127.0.0.1/mydb?user=2106password=Uq3YCioVsO3Dbcp4";
    @Rule
    public OutputCapture capture = new OutputCapture();
    @Mock
    CloudFoundryClient cloudFoundryClient;
    private CreateSecurityGroup createSecurityGroup;

    private static void givenServiceBroker(CloudFoundryClient cloudFoundryClient, String serviceBrokerId, String serviceBrokerName) {
        given(cloudFoundryClient.serviceBrokers()
                .get(GetServiceBrokerRequest.builder()
                        .serviceBrokerId(serviceBrokerId).build()))
                .willReturn(Mono
                        .just(GetServiceBrokerResponse.builder()
                                .entity(ServiceBrokerEntity.builder()
                                        .name(serviceBrokerName)
                                        .build())
                                .build()));
    }

    private static void givenServiceInstance(CloudFoundryClient cloudFoundryClient, String serviceInstanceId, String serviceInstanceName, String servicePlanId) {
        given(cloudFoundryClient.serviceInstances()
                .get(GetServiceInstanceRequest.builder()
                        .serviceInstanceId(serviceInstanceId)
                        .build()))
                .willReturn(Mono
                        .just(GetServiceInstanceResponse.builder()
                                .entity(ServiceInstanceEntity.builder()
                                        .name(serviceInstanceName)
                                        .servicePlanId(servicePlanId)
                                        .build())
                                .build()));
    }

    private static void givenServicePlan(CloudFoundryClient cloudFoundryClient, String servicePlanId, String serviceId) {
        given(cloudFoundryClient.servicePlans()
                .get(GetServicePlanRequest.builder()
                        .servicePlanId(servicePlanId)
                        .build()))
                .willReturn((Mono
                        .just(GetServicePlanResponse.builder()
                                .entity(ServicePlanEntity.builder()
                                        .serviceId(serviceId)
                                        .build())
                                .build())));
    }

    private static void givenService(CloudFoundryClient cloudFoundryClient, String serviceId, String serviceBrokerId) {
        given(cloudFoundryClient.services()
                .get(GetServiceRequest.builder()
                        .serviceId(serviceId)
                        .build()))
                .willReturn((Mono
                        .just(GetServiceResponse.builder()
                                .entity(ServiceEntity.builder()
                                        .serviceBrokerId(serviceBrokerId)
                                        .build())
                                .build())));
    }

    @Before
    public void init() {
        given(cloudFoundryClient.securityGroups())
                .willReturn(Mockito.mock(SecurityGroups.class));
        given(cloudFoundryClient.applicationsV2())
                .willReturn(Mockito.mock(ApplicationsV2.class));
        given(cloudFoundryClient.serviceBrokers())
                .willReturn(Mockito.mock(ServiceBrokers.class));
        given(cloudFoundryClient.serviceInstances())
                .willReturn(Mockito.mock(ServiceInstances.class));
        given(cloudFoundryClient.services())
                .willReturn(Mockito.mock(Services.class));
        given(cloudFoundryClient.servicePlans())
                .willReturn(Mockito.mock(ServicePlans.class));

        final TrustedDestinationSpecification trustedDestinationSpecification = new TrustedDestinationSpecification(
                ImmutableTrustedDestination.builder()
                        .hosts(ImmutableCIDR.of("127.0.0.1/29"))
                        .ports(ImmutablePorts.builder()
                                .addValue(ImmutablePort.of(3306))
                                .build())
                        .build());
        createSecurityGroup = new CreateSecurityGroup(cloudFoundryClient, trustedDestinationSpecification);
    }

    @Test
    public void should_create_security_group() throws Exception {
        givenBoundedAppExists(this.cloudFoundryClient, "app_guid");
        givenServicePlan(this.cloudFoundryClient, "plan-id", "service-id");
        givenService(this.cloudFoundryClient, "service-id", "service-broker-id");
        givenServiceBroker(this.cloudFoundryClient, "service-broker-id", "service-broker-name");
        givenServiceInstance(this.cloudFoundryClient, "service-instance-id", "service-instance-name", "plan-id");
        givenCreateSecurityGroupsSucceeds(this.cloudFoundryClient, "test-securitygroup-name");


        Map<String, Object> credentials = new HashMap<>();
        credentials.put("uri", TEST_URI);

        Map<String, Object> bindResources = new HashMap<>();
        bindResources.put(ServiceBindingResource.BIND_RESOURCE_KEY_APP.toString(), "app_guid");

        createSecurityGroup
                .run(
                        new CreateServiceInstanceBindingRequest("service-id", "plan-id", null, bindResources, null)
                                .withBindingId("test-securitygroup-name")
                                .withServiceInstanceId("service-instance-id"),
                        new CreateServiceInstanceAppBindingResponse().withCredentials(credentials)
                );

        Mockito.verify(cloudFoundryClient.securityGroups())
                .create(CreateSecurityGroupRequest.builder()
                        .name("test-securitygroup-name")
                        .spaceId("space_id")
                        .rule(RuleEntity.builder()
                                .description(RULE_DESCRIPTION)
                                .protocol(Protocol.TCP)
                                .ports("3306")
                                .destination("127.0.0.1")
                                .build())
                        .build());
    }

    @Test(expected = ClientV2Exception.class)
    public void fail_to_create_create_security_group_should_raise_exception_so_that_CC_requests_unbinding_action_to_clean_up_target_broker_related_resources() throws Exception {
        givenBoundedAppExists(this.cloudFoundryClient, "app_guid");
        givenServicePlan(this.cloudFoundryClient, "plan-id", "service-id");
        givenService(this.cloudFoundryClient, "service-id", "service-broker-id");
        givenServiceBroker(this.cloudFoundryClient, "service-broker-id", "service-broker-name");
        givenServiceInstance(this.cloudFoundryClient, "service-instance-id", "service-instance-name", "plan-id");
        givenCreateSecurityGroupsFails(this.cloudFoundryClient, "test-securitygroup-name");

        Map<String, Object> credentials = new HashMap<>();
        credentials.put("uri", TEST_URI);
        Map<String, Object> bindResources = new HashMap<>();
        bindResources.put(ServiceBindingResource.BIND_RESOURCE_KEY_APP.toString(), "app_guid");

        createSecurityGroup
                .run(
                        new CreateServiceInstanceBindingRequest("service-id", "plan-id", null, bindResources, null)
                                .withBindingId("test-securitygroup-name")
                                .withServiceInstanceId("service-instance-id"),
                        new CreateServiceInstanceAppBindingResponse().withCredentials(credentials)
                );

    }

    @Test(expected = ClientV2Exception.class)
    public void should_block_until_create_security_group_returns() throws Exception {
        givenBoundedAppExists(this.cloudFoundryClient, "app_guid");
        givenServicePlan(this.cloudFoundryClient, "plan-id", "service-id");
        givenService(this.cloudFoundryClient, "service-id", "service-broker-id");
        givenServiceBroker(this.cloudFoundryClient, "service-broker-id", "service-broker-name");
        givenServiceInstance(this.cloudFoundryClient, "service-instance-id", "service-instance-name", "plan-id");
        givenCreateSecurityGroupsFailsWithDelay(this.cloudFoundryClient, "test-securitygroup-name");

        Map<String, Object> credentials = new HashMap<>();
        credentials.put("uri", TEST_URI);
        Map<String, Object> bindResources = new HashMap<>();
        bindResources.put(ServiceBindingResource.BIND_RESOURCE_KEY_APP.toString(), "app_guid");

        createSecurityGroup
                .run(
                        new CreateServiceInstanceBindingRequest("service-id", "plan-id", null, bindResources, null)
                                .withBindingId("test-securitygroup-name")
                                .withServiceInstanceId("service-instance-id"),
                        new CreateServiceInstanceAppBindingResponse().withCredentials(credentials)
                );

        Mockito.verify(cloudFoundryClient.securityGroups())
                .create(CreateSecurityGroupRequest.builder()
                        .name("test-securitygroup-name")
                        .spaceId("space_id")
                        .rule(RuleEntity.builder()
                                .description(RULE_DESCRIPTION)
                                .protocol(Protocol.TCP)
                                .ports("3306")
                                .destination("127.0.0.1")
                                .build())
                        .build());
    }

    private void givenCreateSecurityGroupsFails(CloudFoundryClient cloudFoundryClient, String securityGroupName) {
        given(cloudFoundryClient.securityGroups()
                .create(CreateSecurityGroupRequest.builder()
                        .name(securityGroupName)
                        .spaceId("space_id")
                        .rule(RuleEntity.builder()
                                .description(RULE_DESCRIPTION)
                                .protocol(Protocol.TCP)
                                .ports("3306")
                                .destination("127.0.0.1")
                                .build())
                        .build()))
                .willReturn(Mono.error(new ClientV2Exception(null, 999, "test-exception-description", "test-exception-errorCode")));
    }

    private void givenCreateSecurityGroupsFailsWithDelay(CloudFoundryClient cloudFoundryClient, String securityGroupName) {
        given(cloudFoundryClient.securityGroups()
                .create(CreateSecurityGroupRequest.builder()
                        .name(securityGroupName)
                        .spaceId("space_id")
                        .rule(RuleEntity.builder()
                                .description(RULE_DESCRIPTION)
                                .protocol(Protocol.TCP)
                                .ports("3306")
                                .destination("127.0.0.1")
                                .build())
                        .build()))
                .willReturn(Mono
                        .delay(Duration.ofSeconds(2))
                        .then(Mono.error(new ClientV2Exception(null, 999, "test-exception-description", "test-exception-errorCode"))));
    }

    private void givenBoundedAppExists(CloudFoundryClient cloudFoundryClient, String appId) {
        given(cloudFoundryClient.applicationsV2()
                .get(GetApplicationRequest.builder()
                        .applicationId(appId)
                        .build()))
                .willReturn(Mono.just(GetApplicationResponse.builder()
                        .entity(ApplicationEntity.builder()
                                .spaceId("space_id")
                                .build())
                        .build()));
    }

    private void givenCreateSecurityGroupsSucceeds(CloudFoundryClient cloudFoundryClient, String securityGroupName) {
        given(cloudFoundryClient.securityGroups()
                .create(CreateSecurityGroupRequest.builder()
                        .name(securityGroupName)
                        .spaceId("space_id")
                        .rule(RuleEntity.builder()
                                .description("generated by sec group filter broker for service binding<test-securitygroup-name> to allow access to service instance<service-instance-name> created from service broker<service-broker-name>.")
                                .protocol(Protocol.TCP)
                                .ports("3306")
                                .destination("127.0.0.1")
                                .build())
                        .build()))
                .willReturn(Mono.just(CreateSecurityGroupResponse.builder()
                        .entity(SecurityGroupEntity.builder()
                                .name(securityGroupName)
                                .rule(RuleEntity.builder()
                                        .protocol(Protocol.TCP)
                                        .ports("3306")
                                        .destination("127.0.0.1")
                                        .build())
                                .build())
                        .build()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void noHostname() throws Exception {
        CreateServiceInstanceBindingRequest request = new CreateServiceInstanceBindingRequest(null, null, "app_guid", null, null);
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("uri", NO_HOST_URI);
        CreateServiceInstanceAppBindingResponse response = new CreateServiceInstanceAppBindingResponse().withCredentials(credentials);

        createSecurityGroup.run(request, response);
    }

    @Test(expected = IllegalArgumentException.class)
    public void noPort() throws Exception {
        CreateServiceInstanceBindingRequest request = new CreateServiceInstanceBindingRequest(null, null, "app_guid", null, null);
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("uri", NO_PORT_URI);
        CreateServiceInstanceAppBindingResponse response = new CreateServiceInstanceAppBindingResponse().withCredentials(credentials);

        createSecurityGroup.run(request, response);
    }

    @Test(expected = CreateSecurityGroup.NotAllowedDestination.class)
    public void should_reject_security_group_with_destination_out_of_range() throws Exception {
        givenBoundedAppExists(this.cloudFoundryClient, "app_guid");
        givenService(this.cloudFoundryClient, "service-id", "service-broker-id");
        givenServiceBroker(this.cloudFoundryClient, "service-broker-id", "service-broker-name");
        givenServiceInstance(this.cloudFoundryClient, "service-instance-id", "service-instance-name", "plan-id");
        givenCreateSecurityGroupsSucceeds(this.cloudFoundryClient, "test-securitygroup-name");

        Map<String, Object> credentials = new HashMap<>();
        credentials.put("uri", TEST_URI);

        Map<String, Object> bindResources = new HashMap<>();
        bindResources.put(ServiceBindingResource.BIND_RESOURCE_KEY_APP.toString(), "app_guid");
        final TrustedDestinationSpecification trustedDestinationSpecification = new TrustedDestinationSpecification(
                ImmutableTrustedDestination.builder()
                        .hosts(ImmutableCIDR.of("192.168.0.1/29"))
                        .ports(ImmutablePorts.builder()
                                .addValue(ImmutablePort.of(3306))
                                .build())
                        .build());

        CreateSecurityGroup createSecurityGroupWithRestrictiveDestinationRange = new CreateSecurityGroup(cloudFoundryClient, trustedDestinationSpecification);

        createSecurityGroupWithRestrictiveDestinationRange
                .run(
                        new CreateServiceInstanceBindingRequest("service-id", "plan-id", null, bindResources, null)
                                .withBindingId("test-securitygroup-name")
                                .withServiceInstanceId("service-instance-id"),
                        new CreateServiceInstanceAppBindingResponse().withCredentials(credentials)
                );

        Mockito.verify(cloudFoundryClient.securityGroups())
                .create(CreateSecurityGroupRequest.builder()
                        .name("test-securitygroup-name")
                        .spaceId("space_id")
                        .rule(RuleEntity.builder()
                                .description(RULE_DESCRIPTION)
                                .protocol(Protocol.TCP)
                                .ports("3306")
                                .destination("127.0.0.1")
                                .build())
                        .build());
    }

}