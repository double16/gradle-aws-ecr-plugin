package com.patdouble.gradle.awsecr

import com.bmuschko.gradle.docker.DockerExtension
import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import groovy.util.logging.Slf4j
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskCollection

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Gradle plugin class to configure docker tasks with AWS ECR credentials. ECR credentials expire, this plugin will
 * request a fresh token based on AWS credentials rather than ECR credentials.
 */
@SuppressWarnings(['ConsecutiveStringConcatenation', 'SpaceAroundMapEntryColon', 'NoDef', 'LineLength'])
@Slf4j
@SuppressWarnings('CompileStatic')
class AwsecrPlugin implements Plugin<Project> {

    public static final String POPULATE_ECR_CREDENTIALS_NAME = 'populateECRCredentials'

    private static final Pattern AWS_ECR_URL = ~/(?:https:\/\/)?([0-9A-Za-z]+)\.dkr\.ecr\.[a-zA-Z0-9-]+\.amazonaws\.com/

    private static final String REGISTRY_PROPERTY_REPO = 'repository'
    private static final String REGISTRY_PROPERTY_IMAGENAME = 'imageName'
    private static final String REGISTRY_PROPERTY_TAG = 'tags'
    // properties renamed in version 6.0 of the gradle-docker-plugin:
    private static final String REGISTRY_PROPERTY_IMAGE = 'image'
    private static final String REGISTRY_PROPERTY_IMAGES = 'images'

    private static final String AWS_ACCESS_KEY_PROPERTY = 'awsAccessKeyId'
    private static final String AWS_ACCESS_SECRET_PROPERTY = 'awsSecretAccessKey'

    Project project
    PopulateECRCredentials populateECRCredentials

    @SuppressWarnings('CouldBeElvis')
    @Override
    void apply(Project project) {
        assert project
        this.project = project
        project.plugins.apply(DockerRemoteApiPlugin)

        populateECRCredentials = createPopulateECRCredentialsTask()
        //Make sure there is an object to share references
        project.extensions.getByType(DockerExtension).with {
            if (!registryCredentials) {
                registryCredentials = new DockerRegistryCredentials(project)
            }
            populateECRCredentials.registryCredentials = registryCredentials
        }

        project.afterEvaluate {
            TaskCollection<RegistryCredentialsAware> regTasks = project.tasks.withType(RegistryCredentialsAware).matching { !(it in PopulateECRCredentials) }
            configureRegistryCredentials(regTasks)
            regTasks*.dependsOn populateECRCredentials
        }
    }

    protected Task createPopulateECRCredentialsTask() {
        project.task(POPULATE_ECR_CREDENTIALS_NAME, type: PopulateECRCredentials) {
            group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
            description = 'Retrieve and use ECR registryCredentials'
            awsAccessKeyId = project.hasProperty(AWS_ACCESS_KEY_PROPERTY) ? project[AWS_ACCESS_KEY_PROPERTY] : null
            awsSecretAccessKey = project.hasProperty(AWS_ACCESS_SECRET_PROPERTY) ? project[AWS_ACCESS_SECRET_PROPERTY] : null

            credFileDirectory = project.rootProject.file('.gradle')
        }
    }

    protected String findRepository(RegistryCredentialsAware registryCredentialsAware) {
        if (registryCredentialsAware.hasProperty(REGISTRY_PROPERTY_REPO)) {
            return registryCredentialsAware[REGISTRY_PROPERTY_REPO].get() as String
        } else if (registryCredentialsAware.hasProperty(REGISTRY_PROPERTY_IMAGENAME)) {
            return registryCredentialsAware[REGISTRY_PROPERTY_IMAGENAME].get() as String
        } else if (registryCredentialsAware.hasProperty(REGISTRY_PROPERTY_TAG)) {
            return registryCredentialsAware[REGISTRY_PROPERTY_TAG].get().first() as String
        } else if (registryCredentialsAware.hasProperty(REGISTRY_PROPERTY_IMAGE)) {
            return registryCredentialsAware[REGISTRY_PROPERTY_IMAGE].get() as String
        } else if (registryCredentialsAware.hasProperty(REGISTRY_PROPERTY_IMAGES)) {
            return registryCredentialsAware[REGISTRY_PROPERTY_IMAGES].get().first() as String
        }
        log.info("Skipping docker credentials for ${registryCredentialsAware} because we do not know how to find a repository for this type (${registryCredentialsAware.class}")
        null
    }

    /**
     * Extract the ECR repository ID and URL
     * @return map containing 'id' and 'url' keys or empty if repository URL is unknown
     */
    protected Map<String, String> extractEcrInfo(CharSequence repository) {
        if (!repository) {
            return [:]
        }

        Matcher m = AWS_ECR_URL.matcher(repository)
        if (!m) {
            return [:]
        }

        ['id' : m.group(1),
         'url': m.group(0),
        ]
    }

    protected void configureRegistryCredentials(Collection<RegistryCredentialsAware> registryCredentialsAwareCollection) {
        List<Map<String, String>> ecrRepositories = registryCredentialsAwareCollection.collect { extractEcrInfo(findRepository(it)) ?: null }.findAll().unique()

        if (ecrRepositories.size() > 1) {
            throw new InvalidUserDataException("Multiple AWS ECR repositories not yet supported: ${ecrRepositories*.repository}")
        }
        if (ecrRepositories.empty) {
            log.info('No compatible registries found')
            return
        }

        Map<String, String> info = ecrRepositories.first()
        String registryId = info.id
        String registryUrl = info.url
        log.info("Found ECR registry account ID ${registryId} at ${registryUrl}")
        populateECRCredentials.with {
            it.registryId = registryId
            it.registryUrl = registryUrl
        }
    }

}
