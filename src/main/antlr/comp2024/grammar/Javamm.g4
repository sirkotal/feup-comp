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
BOOL : 'boolean' ;
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
BOOLEAN : ('true' | 'false');
ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;
MULTI_LINE_COMMENT : '/*' .*? '*/' -> skip ;
SINGLE_LINE_COMMENT : '//' ~[\r\n]* -> skip ;

program
    : (importDecl)* classDecl EOF
    ;

importDecl
    : IMPORT value+=('main' | 'length' | ID) ('.' value+=('main' | 'length' | ID))* SEMI
    ;

classDecl
    : CLASS name=('main' | 'length' | ID) (EXTENDS extended=('main' | 'length' | ID))?
        LCURLY
        varDecl*
        methodDecl*
        RCURLY
    ;

varDecl
    : type name=('main' | 'length' | ID) SEMI
    ;

type locals[boolean isArray=false, boolean isVarargs=false]
    : type LSQUARE RSQUARE {$isArray=true;}     #ArrayType
    | name= INT VARARGS    {$isVarargs=true;}   #VarargsType
    | name= BOOL                             #BooleanType
    | name= INT                                 #IntType
    | name= 'String'                            #StringType
    | name= ('main' | 'length' | ID)            #ClassType
    ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=('length' | ID)
        LPAREN (param (',' param)*)? RPAREN
        LCURLY varDecl* stmt* RETURN expr SEMI RCURLY
    | (PUBLIC {$isPublic=true;})? STATIC
        VOID name='main'
        LPAREN 'String' LSQUARE RSQUARE paramName=('main' | 'length' | ID) RPAREN
        LCURLY varDecl* stmt* RCURLY
    ;

param
    : type name=('main' | 'length' | ID)
    ;

stmt
    : expr SEMI                                                             #ExprStmt
    | LCURLY stmt* RCURLY                                                   #BlockStmt
    | IF LPAREN expr RPAREN stmt ELSE stmt                                  #IfStmt
    | WHILE LPAREN expr RPAREN stmt                                         #WhileStmt
    | name=('main' | 'length' | ID) EQUALS expr SEMI                        #AssignStmt
    | name=('main' | 'length' | ID) LSQUARE expr RSQUARE EQUALS expr SEMI   #AssignArrayStmt
    ;

expr
    : LPAREN expr RPAREN                                                            #Priority
    | NEW INT LSQUARE expr RSQUARE                                                  #NewArray
    | NEW name=('main' | 'length' | ID) LPAREN (param (',' param)*)? RPAREN         #NewClass
    | expr LSQUARE expr RSQUARE                                                     #ArrayAccess
    | expr DOT LENGTH                                                               #LengthExpr
    | expr DOT name=('main' | 'length' | ID) LPAREN (expr ( COMMA expr )*)? RPAREN  #MethodExpr
    | name=THIS                                                                     #ThisExpr
    | expr op= (MUL | DIV) expr                                                     #BinaryExpr
    | expr op= (ADD | SUB) expr                                                     #BinaryExpr
    | expr op= (LES | GRE) expr                                                     #BinaryExpr
    | expr op= AND expr                                                             #BinaryExpr
    | NOT expr                                                                      #Negation
    | LSQUARE (elem+=expr ( COMMA elem+=expr )*)? RSQUARE                           #ArrayLiteral
    | value=INTEGER                                                                 #IntegerLiteral
    | value=BOOLEAN                                                                 #BooleanLiteral
    | name=('main' | 'length' | ID)                                                 #VarRefExpr
    ;
