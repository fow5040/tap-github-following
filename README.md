# tap-github-following
Testing to see if I can make a Scala based Singer tap

This tap will output a list of users the starting user is following, then a list of all the users each one of the output users is following

The tap also emits a user's "degree_of_removal", i.e. how many "chained follows" exist between the starting user and the output user


### Usage
| Command | Description |
|---      |---          |
| `sbt run`  | Run Tap with sbt |
| `sbt test`  | Run Test Cases |
| `sbt assembly` | Package the tap (self-contained jar) |
| `bin/tap-github-following` *or* `bin\tap-github-following.bat` | Run the tap (needs arguments)|

#### Test Configuration
Run `bin\tap-github-following --config config.json --discover > catalog.json` to test your config file and output a catalog of all available schemas 

Config file expects format:
```
{
    "access_token": "yourPersonalAccessToken",
    "starting_user": "aGithubUserName",

    //optional - can set a maximum number of iterations
    "max_lists_to_get": 10
}
```

#### Initial Use
Run the tap with a target:
`tap-github-following --config config.json | target-your-target > newState.json`

#### Subsequent Use
Run the tap with a target and state file:
`tap-github-following --config config.json --state state.json | target-your-target > newState.json`

