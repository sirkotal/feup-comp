grammar Ipv4;

@header {
    package pt.up.fe.comp2024;
}

INTEGER : [0-9]+ ;
ID : [a-zA-Z_][a-zA-Z_0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : statement+ EOF
    ;

statement
    : address ';'
    ;

address
    : address '.' address
    | value = INTEGER
    ;
