#!/usr/bin/env bash

mysql -uroot -p'test' 'SET GLOBAL local_infile = ON';

echo "SET GLOBAL local_infile = ON"