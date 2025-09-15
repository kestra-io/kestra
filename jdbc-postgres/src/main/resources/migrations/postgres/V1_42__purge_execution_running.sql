-- We must truncate the table as in 0.24 there was a bug that lead to records not purged in this table
truncate table execution_running;