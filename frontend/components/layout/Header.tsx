'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

const navLinks = [
    { href: '/', label: 'Home' },
    { href: '/list', label: 'Documents' },
    { href: '/document/create', label: 'Create' },
];

export default function Header() {
    const pathname = usePathname();

    return (
        <header className="header">
            <h1>Jade Tipi</h1>
            <nav className="nav">
                {navLinks.map(({ href, label }) => {
                    const isActive = pathname === href;
                    return (
                        <Link
                            key={href}
                            href={href}
                            style={isActive ? { fontWeight: 'bold', textDecoration: 'underline' } : {}}
                        >
                            {label}
                        </Link>
                    );
                })}
            </nav>
        </header>
    );
}
