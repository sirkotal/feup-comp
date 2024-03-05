grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMI : ';' ;
DOT : '.' ;
COMMA : ',' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LSQUARE : '[' ;
RSQUARE : ']' ;
MUL : '*' ;
ADD : '+' ;
SUB : '-' ;
DIV : '/' ;
LES : '<' ;
GRE : '>' ;
AND : '&&' ;
NOT : '!' ;
VARARGS : '...' ;

IMPORT : 'import' ;
CLASS : 'class' ;
EXTENDS : 'extends' ;
INT : 'int' ;
BOOLEAN : 'boolean' ;
VOID : 'void' ;
PUBLIC : 'public' ;
STATIC : 'static' ;
RETURN : 'return' ;
NEW: 'new' ;
LENGTH: 'length' ;
THIS: 'this' ;
WHILE: 'while' ;
IF: 'if' ;
ELSE: 'else' ;

INTEGER : '0' | [1-9] [0-9]* ;
ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;
MULTI_LINE_COMMENT : '/*' .*? '*/' -> skip ;
SINGLE_LINE_COMMENT : '//' ~[\r\n]* -> skip ;

program
    : (importDecl)* classDecl EOF
    ;

importDecl
    : IMPORT value+=ID ('.' value+=ID)* SEMI
    ;

classDecl
    : CLASS name=ID (EXTENDS extended=ID)?
        LCURLY
        varDecl*
        methodDecl*
        RCURLY
    ;

varDecl
    : type name=ID SEMI
    | type name='main' SEMI
    ;

type locals[boolean isArray=false, boolean isVarargs=false]
    : type LSQUARE RSQUARE {$isArray=true;}     #ArrayType
    | name= INT VARARGS    {$isVarargs=true;}   #VarargsType
    | name= BOOLEAN                             #BooleanType
    | name= INT                                 #IntType
    | name= 'String'                            #StringType
    | name= ID                                  #ClassType
    ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        LPAREN (param (',' param)*)? RPAREN
        LCURLY varDecl* stmt* RETURN expr SEMI RCURLY
    | (PUBLIC {$isPublic=true;})? STATIC
        VOID name='main'
        LPAREN 'String' LSQUARE RSQUARE paramName=ID RPAREN
        LCURLY varDecl* stmt* RCURLY
    ;

param
    : type name=ID
    ;

stmt
    : expr SEMI                                         #ExprStmt
    | LCURLY stmt* RCURLY                               #BlockStmt
    | IF LPAREN expr RPAREN stmt ELSE stmt              #IfStmt
    | WHILE LPAREN expr RPAREN stmt                     #WhileStmt
    | name=ID EQUALS expr SEMI                          #AssignStmt
    | name=ID LSQUARE expr RSQUARE EQUALS expr SEMI     #AssignArrayStmt
    | RETURN expr SEMI                                  #ReturnStmt
    ;

expr
    : expr op= (MUL | DIV) expr                             #BinaryExpr
    | expr op= (ADD | SUB) expr                             #BinaryExpr
    | expr op= (LES | GRE) expr                             #BinaryExpr
    | expr op= AND expr                                     #BinaryExpr
    | expr LSQUARE expr RSQUARE                             #ArrayAccess
    | expr DOT LENGTH                                       #Length
    | expr DOT ID LPAREN (expr ( COMMA expr )*)? RPAREN     #Method
    | NEW INT LSQUARE expr RSQUARE                          #NewArray
    | NEW classname=ID LPAREN (param (',' param)*)? RPAREN  #NewClassExpr
    | NEW ID LPAREN RPAREN                                  #NewObject
    | NOT expr                                              #Negation
    | LPAREN expr RPAREN                                    #Priority
    | LSQUARE (expr ( COMMA expr )*)? RSQUARE               #ArraySomething
    | value=INTEGER                                         #IntegerLiteral
    | value=('true' | 'false')                              #BooleanLiteral
    | name=ID                                               #VarRefExpr
    | THIS                                                  #ThisExpr
    ;



