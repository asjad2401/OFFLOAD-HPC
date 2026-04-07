next steps: 
the broker-worker service will be bundled together
there is always one broker on network
if a worker is better than broker, it can become broker by asking
client can find broker on local network by itself rather than hardcoded ip
broker must be replaceable in case of failure
make the worker registration dynamic
if no workers are available, broker does the job itself
after registering, worker will inform broker about its capabilities
job division will be proportional to the capability of the worker

future mvp:
if network failure accors mid job, it should be resumed from the point of failure

