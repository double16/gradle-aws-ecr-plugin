package com.patdouble.gradle.awsecr

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.ecr.AmazonECRClientBuilder
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult
import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import groovy.transform.Canonical
import groovy.util.logging.Slf4j
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

import java.security.MessageDigest

/**
 * Task to populate the ECR credentials during the execution phase. A token will be generated by AWS if necessary
 * and cached.
 */
@Slf4j
@SuppressWarnings('CompileStatic')
class PopulateECRCredentials extends DefaultTask implements RegistryCredentialsAware {

    protected static final String CACHE_USERNAME = 'username'
    protected static final String CACHE_PASSWORD = 'password'
    protected static final String CACHE_EXPIRESAT = 'expiresAt'
    protected static final String COLON_SEPARATOR = ':'
    protected static final String PROFILE_KEY = 'AWS_PROFILE'
    protected static final String PROFILE_PROPERTY_KEY = 'aws.profile'

    @Canonical
    static class CachedCredentials {

        String username, password
        Long expiresAt

    }

    @Input
    DockerRegistryCredentials registryCredentials = new DockerRegistryCredentials(project.objects)

    @Input
    String registryId

    @Input
    String registryUrl

    @Input
    @Optional
    String awsAccessKeyId

    @Input
    @Optional
    String awsSecretAccessKey

    String credFileDirectory

    PopulateECRCredentials() {
        onlyIf { registryId && registryUrl?.contains('.ecr.') }
    }

    @TaskAction
    void populateCredentials() {
        File credFile = credentialFile

        CachedCredentials creds = getCachedCredentials(credFile)
        if (!creds) {
            creds = requestCredentials()
            setCachedCredentials(credFile, creds)
        }

        registryCredentials.with {
            url = registryUrl
            username = creds.username
            password = creds.password
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings('ConfusingMethodName')
    void registryCredentials(Action<? super DockerRegistryCredentials> action) {
        action.execute(registryCredentials)
    }

    /**
     * Get the cached credentials, if any.
     * @param credFile the file holding the credentials
     * @return CachedCredentials if valid, null otherwise.
     */
    @SuppressWarnings('UnnecessaryGetter')
    protected CachedCredentials getCachedCredentials(File credFile) {
        if (credFile.canRead() && credFile.isFile()) {
            Properties props = new Properties()
            credFile.withInputStream { props.load(it) }
            if (Long.parseLong(props.getProperty(CACHE_EXPIRESAT, '0')) > (System.currentTimeMillis() + 3600000)) {
                logger.info("Using ECR registryCredentials from ${credFile}")
                return new CachedCredentials(
                    username: props.getProperty(CACHE_USERNAME),
                    password: props.getProperty(CACHE_PASSWORD),
                )
            }
        }
        null
    }

    /**
     * Set the cached credentials in the file.
     */
    protected void setCachedCredentials(File credFile, CachedCredentials creds) {
        Properties props = new Properties()
        props.setProperty(CACHE_USERNAME, creds.username)
        props.setProperty(CACHE_PASSWORD, creds.password)
        props.setProperty(CACHE_EXPIRESAT, String.valueOf(creds.expiresAt))
        credFile.parentFile.mkdirs()
        credFile.withOutputStream { props.store(it, "ECR Credentials @ ${registryUrl}") }
    }

    /**
     * Request ECR credentials from AWS.
     */
    protected CachedCredentials requestCredentials() {
        //Get the region from the registry URL
        String region = registryUrl.replaceAll(/^.*ecr\.(.*)\.amazonaws.*$/, '$1')

        AmazonECRClientBuilder ecrClientBuilder = AmazonECRClientBuilder.standard().withRegion(region)

        if (awsAccessKeyId && awsSecretAccessKey) {
            ecrClientBuilder.credentials = new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(awsAccessKeyId, awsSecretAccessKey))
        }

        GetAuthorizationTokenResult tokens = ecrClientBuilder.build()
                .getAuthorizationToken(new GetAuthorizationTokenRequest().withRegistryIds(registryId))

        if (!tokens.authorizationData) {
            throw new GradleException("Could not get ECR token: ${tokens.sdkHttpMetadata.httpStatusCode}")
        }

        String[] ecrCreds = new String(tokens.authorizationData.first().authorizationToken.decodeBase64(),
            'US-ASCII').split(COLON_SEPARATOR)

        new CachedCredentials(
            username: ecrCreds[0],
            password: ecrCreds[1],
            expiresAt: tokens.authorizationData.first().expiresAt.time,
        )
    }

    /**
     * Get the active AWS profile.
     */
    protected String getProfile() {
        System.getenv(PROFILE_KEY) ?: System.getProperty(PROFILE_PROPERTY_KEY) ?: 'default'
    }

    protected File getCredentialFile() {
        new File("${credFileDirectory}/ecr-${md5(registryId + COLON_SEPARATOR + profile)}.properties")
    }

    @SuppressWarnings('DuplicateStringLiteral')
    protected String md5(String s) {
        MessageDigest digest = MessageDigest.getInstance('MD5')
        digest.update(s.bytes)
        new BigInteger(1, digest.digest()).toString(16).padLeft(32, '0')
    }

}
