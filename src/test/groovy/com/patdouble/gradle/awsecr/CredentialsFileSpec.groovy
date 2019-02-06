package com.patdouble.gradle.awsecr

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.ecr.AmazonECRClient
import com.amazonaws.services.ecr.model.AuthorizationData
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Tests usage of the AWS credentials file that is handled automatically by the default AWS client.
 */
@Unroll
class CredentialsFileSpec extends Specification {
    PopulateECRCredentials task

    @Rule
    TemporaryFolder tmp

    def setupSpec() {
        System.setProperty('user.home', new File(getClass().classLoader.getResource(".aws/credentials").getFile()).parentFile.parentFile.toString())
    }

    def setup() {
        def project = ProjectBuilder.builder().withProjectDir(tmp.newFolder('project')).build()
        project.with {
            apply plugin: 'com.patdouble.awsecr'
        }
        project.evaluate()
        task = project.tasks.getByName('populateECRCredentials')
        task.credFileDirectory = tmp.newFolder('ecr')
        task.registryUrl = AwsecrPluginSpec.FAKE_REPO
        task.registryId = '123456789'
    }

    def cleanup() {
        System.setProperty('aws.profile', '')
    }

    void "should request credentials from profile '#profile'"() {
        given:
        System.setProperty('aws.profile', profile)
        task.credentialFile.delete()

        // Set the DefaultAWSCredentialsProviderChain.INSTANCE field to get the new profile
        def providerChainInstance = DefaultAWSCredentialsProviderChain.getDeclaredField('INSTANCE')
        providerChainInstance.accessible = true
        Field modifiersField = Field.class.getDeclaredField("modifiers")
        modifiersField.accessible = true
        modifiersField.setInt(providerChainInstance, providerChainInstance.getModifiers() & ~Modifier.FINAL)
        providerChainInstance.set(null, new DefaultAWSCredentialsProviderChain())

        def amazonECRClient = GroovySpy(AmazonECRClient, global: true)

        when:
        task.populateCredentials()

        then:
        1 * amazonECRClient.getAuthorizationToken(_) >> { GetAuthorizationTokenRequest request ->
            // we're changing the getAuthorizationToken(...) method to return the AWS credentials so we can verify it later
            AmazonECRClient thisClient = delegate.mockObject.instance
            Field awsCredentialsProviderField = AmazonECRClient.getDeclaredField('awsCredentialsProvider')
            awsCredentialsProviderField.accessible = true
            def awsCredentialsProvider = awsCredentialsProviderField.get(thisClient)
            def credentials = awsCredentialsProvider.getCredentials()
            new GetAuthorizationTokenResult().withAuthorizationData(new AuthorizationData(authorizationToken: "${credentials.AWSAccessKeyId}:${credentials.AWSSecretKey}".bytes.encodeBase64().toString(), expiresAt: new Date() + 7))
        }

        and:
        task.registryCredentials.username.get() == username
        task.registryCredentials.password.get() == password

        where:
        profile    | username              | password
        ''         | 'ACCESS_KEY_DEFAULT'  | 'SECRET_DEFAULT'
        'profile2' | 'ACCESS_KEY_PROFILE2' | 'SECRET_PROFILE2'
    }
}
