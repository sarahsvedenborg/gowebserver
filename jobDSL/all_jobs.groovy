// Your github username
// It should come as a parameter to this seed job
//GITHUB_USERNAME="githubusername"

// Variable re-used in the jobs
PROJ_NAME="webserver"
REPO_URL="https://github.com/${GITHUB_USERNAME}/gowebserver.git"

// Configured to use to define the build pipeline as well
FIRST_JOB_NAME="1.build-${PROJ_NAME}_GEN"

job("${FIRST_JOB_NAME}") {
  logRotator( -1, 5 ,-1 ,-1 )
  scm {
    git {
      remote {
        name('origin')
        url("${REPO_URL}")
      }
      branch('master')
   
      }
    }
  }
  properties {
    environmentVariables {
      keepSystemVariables(true)
      keepBuildVariables(true)
      env('GITHUB_USERNAME', "${GITHUB_USERNAME}")
    }
  }
  triggers {
    scm('* * * * *')
  }
  steps{
    shell('''
echo "version=\$(cat version.txt)" > props.env

imageid=$(sudo docker build -q -t ${GITHUB_USERNAME}/http-app:snapshot . 2>/dev/null | awk -F ":" '{print $2}')

cid=$(sudo docker ps --filter="name=testing-app" -q -a)
if [ ! -z "$cid" ]
then
sudo docker rm -f testing-app
fi

cid=$(sudo docker run -d --name testing-app -p 8001:8000 ${GITHUB_USERNAME}/http-app:snapshot)
echo "cid=$cid" >> props.env
echo "IMAGEID=$imageid" >> props.env
cat props.env
cip=$(sudo docker inspect --format '{{ .NetworkSettings.IPAddress }}' ${cid})
sudo docker run --rm rufus/siege-engine -g http://$cip:8000/
[ $? -ne 0 ] && exit 1
sudo docker kill ${cid}
sudo docker rm ${cid}''')
  }
  publishers {
    downstreamParameterized {
      trigger("2.test-${PROJ_NAME}_GEN") {
        condition('SUCCESS')
        parameters{
          predefinedProp('GITHUB_USERNAME', '${GITHUB_USERNAME}')
          gitRevision(false)
          propertiesFile('props.env', failTriggerOnMissing = true)
        }
      }
    }
  }
}



job("2.test-${PROJ_NAME}_GEN") {
  logRotator( -1, 40 ,-1 ,-1 )
    parameters {
      stringParam('GITHUB_USERNAME', '', 'GITHUB_USERNAME')
      stringParam('version', '', 'version of the application')
      stringParam('IMAGEID', '', 'The docker image to test')
      stringParam('cid', '', 'The container ID')
    }
  steps {
    shell('''
cid=$(sudo docker ps --filter="name=testing-app" -q -a)
if [ ! -z "$cid" ]
then
sudo docker rm -f testing-app
fi
testing_cid=$(sudo docker run -d --name testing-app -p 8000:8000  $IMAGEID)
echo "testing_cid=$testing_cid" > props.env
''')
    environmentVariables {
      propertiesFile('props.env')
    }
    shell('''
cip=$(sudo docker inspect --format '{{ .NetworkSettings.IPAddress }}' ${testing_cid})
sudo docker run --rm rufus/siege-engine  -b -t60S http://$cip:8000/ > output 2>&1
''')
    shell('''
avail=$(cat output | grep Availability | awk '{print $2}')
echo $avail
# shell uses = to compare strings, bash ==
if [ "$avail" = "100.00" ]
then
	echo "Availability high enough"
	sudo docker tag -f $IMAGEID ${GITHUB_USERNAME}/http-app:stable
	exit 0
else
	echo "Availability too low"
	exit 1
fi
''')

  }
  publishers {
    downstreamParameterized {
      trigger("3.release-${PROJ_NAME}_GEN") {
        condition('SUCCESS')
        parameters{
          predefinedProp('VERSION', '${version}')
          predefinedProp('GITHUB_USERNAME', '${GITHUB_USERNAME}')        }
      }
    }
  }
}



job("3.release-${PROJ_NAME}_GEN") {
  logRotator( -1, 5 ,-1 ,-1 )
    parameters {
      stringParam('GITHUB_USERNAME', '', 'GITHUB_USERNAME')
      stringParam('VERSION', '', 'version of the application')
    }
  steps {
    shell('''
sudo docker tag -f ${GITHUB_USERNAME}/http-app:stable ${GITHUB_USERNAME}/http-app:latest
sudo docker tag -f ${GITHUB_USERNAME}/http-app:stable ${GITHUB_USERNAME}/http-app:$VERSION
# no git here yet
# sudo docker tag http-app/http-app:$(git describe)
cid=$(sudo docker ps --filter="name=deploy-app" -q -a)
if [ ! -z "$cid" ]
then
sudo docker rm -f deploy-app
fi
sudo docker run -d --name deploy-app -p 9999:8000 ${GITHUB_USERNAME}/http-app:latest
''')
    shell('''
sudo docker ps |grep ${GITHUB_USERNAME}/http-app
sudo docker images |grep ${GITHUB_USERNAME}/http-app
''')
  }
}





listView("${PROJ_NAME}-jobs_GEN") {
  description("All ${PROJ_NAME} project related jobs")
  jobs {
    regex(".*-${PROJ_NAME}.*")
  }
  columns {
    status()
    weather()
    name()
    lastSuccess()
    lastFailure()
    lastDuration()
    buildButton()
    }
}



buildPipelineView("${PROJ_NAME}-pipeline_GEN") {
  title("Project ${PROJ_NAME} CI Pipeline")
  displayedBuilds(50)
  selectedJob("${FIRST_JOB_NAME}")
  alwaysAllowManualTrigger()
  showPipelineParametersInHeaders()
  showPipelineParameters()
  showPipelineDefinitionHeader()
  refreshFrequency(60)
}
