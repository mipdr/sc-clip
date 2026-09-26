#!/bin/bash
# Wrapper script to run SuperCollider test and capture exit code

# Run sclang with the test script and capture all output
OUTPUT=$(sclang Tests/test_save_load_simple.scd 2>&1 &)
SCLANG_PID=$!

# Wait up to 30 seconds for completion
COUNTER=0
while [ $COUNTER -lt 30 ]; do
	if ! kill -0 $SCLANG_PID 2>/dev/null; then
		# Process finished
		break
	fi
	sleep 1
	COUNTER=$((COUNTER + 1))
done

# Kill if still running
if kill -0 $SCLANG_PID 2>/dev/null; then
	echo "ERROR: Test timed out after 30 seconds"
	kill $SCLANG_PID 2>/dev/null
	exit 1
fi

# Check output for success
if echo "$OUTPUT" | grep -q "ALL TESTS PASSED"; then
	echo "$OUTPUT"
	exit 0
elif echo "$OUTPUT" | grep -q "TESTS FAILED"; then
	echo "$OUTPUT"
	exit 1
else
	echo "$OUTPUT"
	echo "ERROR: Test did not complete properly"
	exit 1
fi
