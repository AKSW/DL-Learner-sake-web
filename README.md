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

The configuration options are docummented on https://cdn.rawgit.com/AKSW/DL-Learner/develop/interfaces/doc/configOptions.html

## Installation prerequisites

- SemWeb2NL current snapshot from https://github.com/AKSW/SemWeb2NL/tree/develop (`git checkout develop`!).

## Result presentation

- Results can be fetched from `/list` for all jobs
- or /`{id}` for a certain job
- If there is an exception, it will be in the `exception` Key together with the Java message string
- Otherwise
    - While Algorithm is processing
        - Key `_state` reads `waiting`
        - Key \`{identifierOfAlgorithmBean}` contains Algorithm details
            - While algorithm is running
                - `_state` is `running`
                - `current best description` is Manchester description
            - when finished
                - a list of top 10 results is produced
                - Each element contains those keys
                    - `description`  Manchester-description
                    - `description.ast` syntax tree of description
                    - `verbalisation` textual verbalisation
                    - `predictive accuracy` 
                    - `F-measure`
                        - measures depend on configured measure
            - while stopped
                - `not running`
    - when finished
        - Key `_state` reads `done`
