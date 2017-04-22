package com.patdouble.gradle.awsecr

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.ecr.AmazonECRClient
import com.amazonaws.services.ecr.model.AuthorizationData
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import com.patdouble.gradle.awsecr.PopulateECRCredentials.CachedCredentials

import java.util.concurrent.TimeUnit

class PopulateECRCredentialsSpec extends Specification {
    PopulateECRCredentials task
    AmazonECRClient amazonECRClient

    @Rule TemporaryFolder tmp

    def setup() {
        amazonECRClient = GroovySpy(AmazonECRClient, global: true)

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
    
    void "should save credentials"() {
        given:
        def creds = new CachedCredentials(username: 'user1', password: 'pw', expiresAt: 1234L)
        def file = tmp.newFile('ecrtest.properties')
        when:
        task.setCachedCredentials(file, creds)
        Properties props = new Properties()
        file.withReader { props.load(it) }
        then:
        props.getProperty(PopulateECRCredentials.CACHE_USERNAME) == 'user1'
        props.getProperty(PopulateECRCredentials.CACHE_PASSWORD) == 'pw'
        props.getProperty(PopulateECRCredentials.CACHE_EXPIRESAT) == '1234'
    }

    void "should read valid credentials"() {
        given:
        def creds = new CachedCredentials(username: 'user1', password: 'pw', expiresAt: System.currentTimeMillis()+ TimeUnit.DAYS.toMillis(7))
        def file = tmp.newFile('ecrtest.properties')
        task.setCachedCredentials(file, creds)
        when:
        CachedCredentials loadedCreds = task.getCachedCredentials(file)
        then:
        loadedCreds.username == 'user1'
        loadedCreds.password == 'pw'
    }

    void "should not read invalid credentials"() {
        given:
        def creds = new CachedCredentials(username: 'user1', password: 'pw', expiresAt: 1234L)
        def file = tmp.newFile('ecrtest.properties')
        task.setCachedCredentials(file, creds)
        when:
        CachedCredentials loadedCreds = task.getCachedCredentials(file)
        then:
        loadedCreds == null
    }

    void "should not fail on missing credentials"() {
        given:
        def file = tmp.newFile('ecrtest.properties')
        expect:
        task.getCachedCredentials(file) == null
    }

    void "should use different caches for different registries"() {
        given:
        task.registryUrl = 'https://123456789.dkr.ecr.us-east-1.amazonaws.com'
        task.registryId = '123456789'
        def file1 = task.getCredentialFile()
        task.registryUrl = 'https://987654321.dkr.ecr.us-east-1.amazonaws.com'
        task.registryId = '987654321'
        def file2 = task.getCredentialFile()
        expect:
        file1.absolutePath != file2.absolutePath
    }

    void "should request credentials from AWS"() {
        given:
        def tokens = new GetAuthorizationTokenResult().withAuthorizationData(new AuthorizationData(authorizationToken: 'user1:pw'.bytes.encodeBase64().toString(), expiresAt: new Date() + 7))
        when:
        task.runReactiveStream()
        then:
        1 * amazonECRClient.getAuthorizationToken(new GetAuthorizationTokenRequest().withRegistryIds('123456789')) >> tokens
        and:
        task.registryCredentials.username == 'user1'
        task.registryCredentials.password == 'pw'
    }

    void "should request credentials with AWS keys"() {
        given:
        def tokens = new GetAuthorizationTokenResult().withAuthorizationData(new AuthorizationData(authorizationToken: 'user1:pw'.bytes.encodeBase64().toString(), expiresAt: new Date() + 7))
        def request = new GetAuthorizationTokenRequest().withRegistryIds('123456789')
        task.awsAccessKeyId = 'aws_access_key'
        task.awsSecretAccessKey = 'aws_secret_key'
        request.setRequestCredentialsProvider(new AWSStaticCredentialsProvider(
                new BasicAWSCredentials('aws_access_key', 'aws_secret_key')))
        when:
        task.runReactiveStream()
        then:
        1 * amazonECRClient.getAuthorizationToken(request) >> tokens
        and:
        task.registryCredentials.username == 'user1'
        task.registryCredentials.password == 'pw'
    }

    void "should configure cached credentials"() {
        given:
        def creds = new CachedCredentials(username: 'cacheduser', password: 'cachedpw', expiresAt: (new Date()+7).time)
        task.setCachedCredentials(task.getCredentialFile(), creds)
        when:
        task.runReactiveStream()
        then:
        0 * amazonECRClient.getAuthorizationToken(_)
        and:
        task.registryCredentials.username == 'cacheduser'
        task.registryCredentials.password == 'cachedpw'
    }

    void "should configure refreshed credentials"() {
        given:
        def tokens = new GetAuthorizationTokenResult().withAuthorizationData(new AuthorizationData(authorizationToken: 'user1:pw'.bytes.encodeBase64().toString(), expiresAt: new Date() + 7))
        def creds = new CachedCredentials(username: 'cacheduser', password: 'cachedpw', expiresAt: (new Date()-7).time)
        task.setCachedCredentials(task.getCredentialFile(), creds)
        when:
        task.runReactiveStream()
        then:
        1 * amazonECRClient.getAuthorizationToken(new GetAuthorizationTokenRequest().withRegistryIds('123456789')) >> tokens
        and:
        task.registryCredentials.username == 'user1'
        task.registryCredentials.password == 'pw'
    }
}

