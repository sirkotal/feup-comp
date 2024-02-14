grammar RISC;

@header {
    package pt.up.fe.comp2024;
}

WS: [ \t\r\n]+ -> skip;

program
    : instruction+ EOF
    ;

instruction
    : add | sub | lw | sw ';'
    ;

add
    : 'add' register ',' register ',' register;
sub
    : 'sub' register ',' register ',' register;
lw
    : 'lw' register ',' INT '(' register ')';
sw
    : 'sw' register ',' INT '(' register ')';

register: 'x' INT;
INT: [0-9]+;
