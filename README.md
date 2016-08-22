# DL-Learner-sake-web

Rest interface to DL-Learner structured machine learning application

## Running

```
mvn jetty:run
```

## Endpoints

```
/       GET     short endpoint summary
/list	GET	    get details of all jobs
/submit	POST	submit a new job
/{id}	GET	    get details of job {id}
/{id}	DELETE	delete job {id}
```

## Config format

JSON

See https://github.com/AKSW/DL-Learner/tree/develop/examples 
for original config and https://github.com/AKSW/DL-Learner-sake-web/tree/develop/examples 
for the json conversion.
