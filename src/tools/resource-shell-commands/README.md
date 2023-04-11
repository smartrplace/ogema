# Resource shell commands

This module adds Gogo shell commands for OGEMA resource handling. There are two classes of commands, one for [generic resources](#resource-commands), the other for [schedules and timeseries in general](#schedule-commands). Their namespaces are `resource` and `schedule`, respectively.

A general principle of these commands is that they accept either a resource path or the resource object itself as input. Note that it is possible to store Gogo shell objects in variables. These variables can later be accessed by prefixing their name with `$`, e.g.:

```shell
r = resource:getresource path/to/resource
$r getsubresources false
```

This way it is also possible to use all methods defined on a resource in the shell, as well.

## Resource commands

General remarks:
* Many commands take an optional parameter `-t` for specifying a **resource type**, or require a resource type as mandatory parameter. In this case the fully-qualified type name must be provided, such as `org.ogema.model.locations.Room`, except for some well-known resource types (many although not all types defined in the [OGEMA API](https://github.com/smartrplace/ogema/tree/public/src/core/models/src/main/java/org/ogema/model) and [models](https://github.com/smartrplace/ogema/tree/public/src/core/models/src/main/java/org/ogema/model) projects). For instance, the type `Room` is recognized as an alias for `org.ogema.model.locations.Room`, the type `FloatResource` is an alias for `org.ogema.core.model.simple.FloatResource`, etc.

### addsubresource

Example:

```shell
addsubresource path/to/resource childName -t AbsoluteSchedule
```
adds a subresource named *childName* to the resource at location *path/to/resource* of type `org.ogema.core.model.schedule.AbsoluteSchedule`.  

Parameters:
* Resource or resource path
* Child resource name

Options:
* `-t <RESOURCE_TYPE>`: specify the resource type of the subresource. If the type is specified in the model declaration of the parent then the parameter can be skipped.
* `-a`: activate the new subresource immediately
* `-v <VALUE>`: for SingleValueResources: set the initial value for the new resource.

### addtolist

```shell
addtolist path/to/resource/list -n childName
```

Parameters:
* Resource list or resource list path
Options:
* `-n <CHILD_NAME>`: optionally, specify a name for the new subresource

### createresource

Example:

```shell
createresource mySensor TemperatureSensor
```
creates a new resource named *mySensor* of type `org.ogema.model.sensors.TemperatureSensor`.  

Parameters:
* Resource path
* Resource type

Options:
* `-a`: activate the new resource immediately
* `-v <VALUE>`: for SingleValueResources: set the initial value for the new resource.

### createresourcelist

Example:

```shell
createresourcelist myTempSensors TemperatureSensor
```
creates a new resource list named *myTempSensors* with element type `org.ogema.model.sensors.TemperatureSensor`.  

Parameters:
* Resource path
* Resource type


### getresource

Example:

```shell
getresource path/to/resource
```

Parameters:
* Resource path

### getsubresource

Example:

```shell
getsubresource path/to/resource/reading program -t AbsoluteSchedule
```

Parameters:
* Parent resource or resource path
* Subresource name

Options:
* `-t <RESOURCE_TYPE>`: specify the resource type. Not required if the subresource exists.


### getsubresources

Example:

```shell
getsubresources path/to/resource -t AbsoluteSchedule
```

Parameters:
* Resource or resource path

Options:
* `-t <RESOURCE_TYPE>`: specify the resource type

### numresources

Example:

```shell
numresources -t AbsoluteSchedule
```

Options:
* `-t <RESOURCE_TYPE>`: specify the resource type
* `-top`: include only toplevel resources

### numsubresources

Example:

```shell
numsubresources path/to/resource -t Room
```

Parameters:
* Resource or resource path

Options:
* `-t <RESOURCE_TYPE>`: specify the resource type

### toplevelresources

Example:

```shell
toplevelresources -t Room
```

Options:
* `-t <RESOURCE_TYPE>`: specify the resource type

## Schedule commands

All commands, except [addvalues](#addvalues), [deletevalues](#deletevalues) and [setinterpolationmode](#setinterpolationmode), accept both schedules (timeseries resources), logged single value resources and other generic timeseries as input.

### addvalues

Example1:

```shell
addvalues path/to/schedule 2023-03-04T12:32:00Z=23.4,2023-03-04T12:45:00Z=25.2
```

Example2:

```shell
addvalues path/to/schedule now+5min=23.4,now+10min=25.2
```

Parameters:
* Schedule or schedule path
* Values, in the format &lt;TIMESTAMP1&gt;=&lt;VALUE1&gt;,&lt;TIMESTAMP2&gt;=&lt;VALUE2&gt;, where TIMESTAMP can be either of:
    * millis since epoch (1st Jan 1970), e.g.: 1677945953000
    * an ISO-like formatted string, such as `2023-03-04T12:32:00Z`, `2023-03-04T12:32Z`, or `2023-03-04Z`. The timezone is optional, but recommended.
    * a format such as
        * now-30s
        * now+5min
        * now+1d
  	
Options:
* `-f <FORMAT>`: specify the value format. `f`: float (default), `d`: double, `i`: int, `l`: long, `b`: boolean, `s`: string
* `-a`: activate schedule

### deletevalues

Example:

```shell
deletevalues path/to/schedule -s 2023-03-04T12:32:00Z -e 2023-03-10T10:01:00Z
```

Parameters:
* Schedule or schedule path

Options:
* `-s <START_TIME>`: start time. For formats see method [addvalues](#addvalues).
* `-e <END_TIME>`: end time. For formats see method [addvalues](#addvalues).

If no start time and end time is specified, all values will be deleted.


### firsttimestamp

Example:

```shell
firsttimestamp path/to/schedule -s 2023-03-02T12:00Z
```

Parameters:
* Schedule or SingleValueResource or generic timeseries, or the corresponding resource path

Options:
* `-s <START_TIME>`: start time. For formats see method [addvalues](#addvalues).


### getinterpolationmode

Example:

```shell
(getinterpolationmode path/to/schedule) tostring
```

Parameters:
* Schedule or SingleValueResource or generic timeseries, or the corresponding resource path

### getvalues

Get the values of the schedule. For purposes of visualizing the results it is usually more appropriate to use [printvalues](#printvalues) instead, see below.

### lasttimestamp

Example:

```shell
lasttimestamp path/to/schedule -e 2023-03-02T12:00Z
```

Parameters:
* Schedule or SingleValueResource or generic timeseries, or the corresponding resource path

Options:
* `-e <END_TIME>`: end time. For formats see method [addvalues](#addvalues).


### printvalues

Example:

```shell
printvalues path/to/schedule -l 10 -s 2023-03-02T12:00Z
```

Parameters:
* Schedule or SingleValueResource or generic timeseries, or the corresponding resource path

Options:
* `-s <START_TIME>`: start time. For formats see method [addvalues](#addvalues).
* `-e <END_TIME>`: end time. For formats see method [addvalues](#addvalues).
* `-l <LIMIT>`: limit (int)
* `-fb`: from-beginning: only relevant if a limit is set. If provided, then values from the beginning of the specified interval are returned, else from the end.

### setinterpolationmode

Example:

```shell
setinterpolationmode path/to/schedule STEPS
```

Parameters:
* Schedule or SingleValueResource or generic timeseries, or the corresponding resource path
* Interpolation mode: `STEPS`, `LINEAR`, or `NONE`

### size

Example:

```shell
size path/to/schedule -s 2023-03-02T12:00Z -e 2023-03-03T00:00Z
```

Parameters:
* Schedule or SingleValueResource or generic timeseries, or the corresponding resource path

Options:
* `-s <START_TIME>`: start time. For formats see method [addvalues](#addvalues).
* `-e <END_TIME>`: end time. For formats see method [addvalues](#addvalues).




