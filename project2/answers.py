import datetime
from decimal import *

correctanswers = ["" for i in range(0, 4)]

correctanswers[1] = [('ATL', 1), ('BOS', 4), ('DEN', 0), ('DFW', 2), ('FLL', 0), ('IAD', 0), ('JFK', 1), ('LAX', 0), ('OAK', 1), ('ORD', 0)]

correctanswers[2] =  [('Dallas Fort Worth International                                                                     ', Decimal('0.38')), ('John F Kennedy International                                                                        ', Decimal('0.33')), ('Los Angeles International                                                                           ', Decimal('0.33')), ('Fort Lauderdale Hollywood International                                                             ', Decimal('0.22')), ('Hartsfield Jackson Atlanta International                                                            ', Decimal('0.14')), ('Washington Dulles International                                                                     ', Decimal('0.14')), ('Metropolitan Oakland International                                                                  ', Decimal('0.11')), ("Chicago O'Hare International                                                                        ", Decimal('0.00')), ('Denver International                                                                                ', Decimal('0.00')), ('General Edward Lawrence Logan International                                                         ', Decimal('0.00'))]

correctanswers[3] = [('cust10    ',), ('cust106   ',), ('cust109   ',), ('cust118   ',), ('cust123   ',), ('cust29    ',), ('cust3     ',), ('cust56    ',), ('cust85    ',), ('cust89    ',), ('cust94    ',), ('cust98    ',)]