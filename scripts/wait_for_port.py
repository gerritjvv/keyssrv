#!/usr/bin/env python

import time
import socket
import sys

def wait_for_port(port, host='localhost', retries=1):
    """Wait until a port starts accepting TCP connections.
    Args:
        port (int): Port number.
        host (str): Host address on which the port should exist.
        timeout (float): In seconds. How long to wait before raising errors.
    Raises:
        TimeoutError: The port isn't accepting connection after time specified in `timeout`.
    """
    counter = 0

    while True:
        try:
            s = socket.socket()

            s.connect((host, port))
            s.close()
            break
        except:
            time.sleep(1)
            if counter > retries:
                print("Timeout waiting for port {}".format(port))
                raise

            print("Waiting for port {}".format(port))

            counter = counter + 1

if __name__ == "__main__":
    wait_for_port(int(sys.argv[1]))