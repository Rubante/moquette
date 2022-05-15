CREATE TABLE `account` (
   `login` varchar(50) DEFAULT NULL,
   `username` varchar(50) DEFAULT NULL,
   `password` varchar(64) DEFAULT NULL,
   UNIQUE KEY `idx_username` (`username`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
