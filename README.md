# Mansoon

A RESTful API for accessing data on https://www.collaction.hk/lab/extradition_gallery

## Get started

You must install the [Clojure Cli](https://clojure.org/guides/getting_started)

Start the development environment. The first time startup will be quite slow, since it will fetch all data from extradition_gallery
to your local db

```
# At terminal
bin/nrepl
# At REPL
(dev) (start)
```

After changing code, you can refresh the code and restart the application

```
(refresh) (reset)
```

You can stop the application by

```
(stop)
```

## Production

```
# Start App in Production Mode
bin/start
# Stop App
bin/stop
```

## License

Copyright (c) 2018-2020 Albert Lai

Distributed under the Eclipse Public License 2.0.
