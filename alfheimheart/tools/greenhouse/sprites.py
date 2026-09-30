"""Hand-drawn little sprites (leaves, flowers) and their palette, placed by the generators.

Keys: o outline, d/m/l/h leaf greens dark..highlight, O/p/P/q/w pink petals dark..white,
Y/y yellow heart, X/V/W white petals dark..white.
"""
from pix import hexc

PAL = {
    'o': hexc('173D1C'), 'd': hexc('2F6E2A'), 'm': hexc('4E9C35'), 'l': hexc('7CCB47'), 'h': hexc('C4F08A'),
    'O': hexc('6E2350'), 'p': hexc('C45A97'), 'P': hexc('EE8FC2'), 'q': hexc('FFC4E2'), 'w': hexc('FFF0F8'),
    'Y': hexc('FFD84A'), 'y': hexc('D69A1C'),
    'X': hexc('5C4E62'), 'V': hexc('C9C0D6'), 'W': hexc('FAF8FF'),
    'b': hexc('1B64B8'), 'c': hexc('55D9F7'), 'C': hexc('A6F6FF'), 'k': hexc('0B2F5C'),
}

# leaves grow from their bottom-left corner up and to the right
LEAF_DIAG = ['....ooo',
             '...olho',
             '..olmlo',
             '.olmmlo',
             '.ommdo.',
             'odddo..',
             '.oo....']
LEAF_DIAG_S = ['...ooo.',
               '..ollho',
               '.olmmlo',
               'olmmdo.',
               'omddo..',
               '.ooo...']
LEAF_BIG = ['.....oooo.',
            '...oolllho',
            '..ollmmmlo',
            '.olmmmmdo.',
            'olmddddo..',
            'omdoooo...',
            '.oo.......']
LEAF_RIGHT = ['..ooo...',
              '.ollloo.',
              'ommmmmlo',
              '.odddoo.',
              '..ooo...']
LEAF_TINY = ['.ooo.',
             'ollmo',
             '.oddo',
             '..oo.']
BLOSSOM = ['.OO.OO.',
           'OqqOqPO',
           'OqwqPPO',
           '.OPYPO.',
           'OPPpPpO',
           'OPpOppO',
           '.OO.OO.']
BLOSSOM_S = ['.O.O.',
             'OqOPO',
             '.OYO.',
             'OPOpO',
             '.O.O.']
DAISY = ['..X.X..',
         '.XWXWX.',
         'XWWWWWX',
         '.XWYWX.',
         'XWWWWWX',
         '.XWXWX.',
         '..X.X..']
BUD = ['.oO.',
       'oPqO',
       'OpPO',
       '.OO.']


def flip_h(rows):
    return [r[::-1] for r in rows]


def flip_v(rows):
    return list(reversed(rows))


def rot90(rows):
    """Turns a sprite a quarter clockwise."""
    h, w = len(rows), max(len(r) for r in rows)
    rows = [r.ljust(w, '.') for r in rows]
    return [''.join(rows[h - 1 - j][i] for j in range(h)) for i in range(w)]
