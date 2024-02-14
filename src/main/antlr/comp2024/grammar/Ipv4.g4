grammar Ipv4;

@header {
    package pt.up.fe.comp2024;
}

OCTET: [0-9] | [1-9] [0-9] | '1' [0-9] [0-9] | '2' [0-4] [0-9] | '2' '5' [0-5];
SEP: '.';

program
    : address+ EOF
    ;

address
    : o1=OCTET '.' o2=OCTET '.' o3=OCTET '.' o4=OCTET ';'
    ;
